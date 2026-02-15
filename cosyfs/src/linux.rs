use libc::{
    O_CLOEXEC, O_CREAT, O_DIRECTORY, O_NOFOLLOW, O_RDONLY, O_TRUNC, O_WRONLY, c_int, mode_t,
};
use std::{
    ffi::{CStr, CString},
    io::{Read, Write},
    os::unix::io::{FromRawFd, RawFd},
};

use crate::error::CosyfsError;

// ---- openat2 syscall glue (Linux) ----

#[repr(C)]
#[derive(Default)]
struct OpenHow {
    flags: u64,
    mode: u64,
    resolve: u64,
}

// Values come from Linux UAPI; keep local to avoid extra deps.
const SYS_OPENAT2: libc::c_long = 437; // On x86_64. NOTE: arch-dependent.

const RESOLVE_NO_XDEV: u64 = 0x01;
const RESOLVE_NO_MAGICLINKS: u64 = 0x02;
const RESOLVE_NO_SYMLINKS: u64 = 0x04;
const RESOLVE_BENEATH: u64 = 0x08;

fn openat2(dirfd: RawFd, path: &CStr, how: &OpenHow) -> Result<RawFd, c_int> {
    let ret = unsafe {
        libc::syscall(
            SYS_OPENAT2,
            dirfd,
            path.as_ptr(),
            how as *const OpenHow,
            std::mem::size_of::<OpenHow>(),
        )
    };
    if ret < 0 {
        Err(errno())
    } else {
        Ok(ret as RawFd)
    }
}

fn errno() -> c_int {
    unsafe { *libc::__errno_location() }
}

// Validates a “relative path” without ".." and without leading "/".
fn validate_rel(rel: &str) -> Result<(), &'static str> {
    if rel.is_empty() {
        return Err("path must not be empty");
    }
    if rel.starts_with('/') {
        return Err("path must be relative");
    }
    for part in rel.split('/') {
        if part.is_empty() || part == "." {
            continue;
        }
        if part == ".." {
            return Err("path must not contain '..'");
        }
    }
    Ok(())
}

fn cstr(s: &str) -> Result<CString, CosyfsError> {
    CString::new(s).map_err(|_| CosyfsError::from_errno(libc::EINVAL, "string contains NUL"))
}

fn open_root_dir(root: &str) -> Result<RawFd, CosyfsError> {
    let c = cstr(root)?;
    // open root directory itself safely (no symlinks)
    let fd = unsafe { libc::open(c.as_ptr(), O_RDONLY | O_DIRECTORY | O_CLOEXEC | O_NOFOLLOW) };
    if fd < 0 {
        return Err(CosyfsError::from_errno(
            errno(),
            "failed to open root directory",
        ));
    }
    Ok(fd)
}

// Secure open under root using openat2 resolve flags.
fn open_under_root(
    rootfd: RawFd,
    rel: &str,
    flags: i32,
    mode: mode_t,
) -> Result<RawFd, CosyfsError> {
    validate_rel(rel).map_err(|m| CosyfsError::from_errno(libc::EINVAL, m))?;
    let c = cstr(rel)?;

    let how = OpenHow {
        flags: (flags as u64) | (O_CLOEXEC as u64),
        mode: mode as u64,
        resolve: RESOLVE_BENEATH | RESOLVE_NO_SYMLINKS | RESOLVE_NO_MAGICLINKS | RESOLVE_NO_XDEV,
    };

    openat2(rootfd, &c, &how).map_err(|e| CosyfsError::from_errno(e, "openat2 failed"))
}

pub fn read_file(root: &str, rel: &str, max_bytes: usize) -> Result<Vec<u8>, CosyfsError> {
    let rootfd = open_root_dir(root)?;
    let fd = match open_under_root(rootfd, rel, O_RDONLY, 0) {
        Ok(fd) => fd,
        Err(e) => {
            unsafe {
                libc::close(rootfd);
            }
            return Err(e);
        }
    };

    let mut file = unsafe { std::fs::File::from_raw_fd(fd) };
    unsafe {
        libc::close(rootfd);
    }

    let meta = file
        .metadata()
        .map_err(|_| CosyfsError::from_errno(errno(), "metadata failed"))?;
    if meta.is_dir() {
        return Err(CosyfsError::from_errno(libc::EISDIR, "path is a directory"));
    }
    if meta.len() as usize > max_bytes {
        return Err(CosyfsError::from_errno(libc::EFBIG, "file too large"));
    }

    let mut buf = Vec::with_capacity(meta.len() as usize);
    file.read_to_end(&mut buf)
        .map_err(|_| CosyfsError::from_errno(errno(), "read failed"))?;
    Ok(buf)
}

pub fn write_file_truncate(
    root: &str,
    rel: &str,
    data: &[u8],
    mode: mode_t,
) -> Result<(), CosyfsError> {
    let rootfd = open_root_dir(root)?;
    let fd = match open_under_root(rootfd, rel, O_WRONLY | O_CREAT | O_TRUNC, mode) {
        Ok(fd) => fd,
        Err(e) => {
            unsafe {
                libc::close(rootfd);
            }
            return Err(e);
        }
    };
    let mut file = unsafe { std::fs::File::from_raw_fd(fd) };
    unsafe {
        libc::close(rootfd);
    }

    file.write_all(data)
        .map_err(|_| CosyfsError::from_errno(errno(), "write failed"))?;
    file.sync_all().ok(); // best-effort durability
    Ok(())
}

// NOTE: renameat2 could be added for atomic replace semantics.
pub fn rename_path(root: &str, old_rel: &str, new_rel: &str) -> Result<(), CosyfsError> {
    validate_rel(old_rel).map_err(|m| CosyfsError::from_errno(libc::EINVAL, m))?;
    validate_rel(new_rel).map_err(|m| CosyfsError::from_errno(libc::EINVAL, m))?;

    let rootfd = open_root_dir(root)?;
    let oldc = cstr(old_rel)?;
    let newc = cstr(new_rel)?;

    // renameat2 is nicer; but plain renameat works fd-relative too.
    let r = unsafe { libc::renameat(rootfd, oldc.as_ptr(), rootfd, newc.as_ptr()) };
    let err = if r < 0 { Some(errno()) } else { None };
    unsafe {
        libc::close(rootfd);
    }

    if let Some(e) = err {
        return Err(CosyfsError::from_errno(e, "rename failed"));
    }
    Ok(())
}

// Minimal unlink (file). For recursive delete, implement fd-walk with openat2 + getdents.
pub fn delete_file(root: &str, rel: &str) -> Result<(), CosyfsError> {
    validate_rel(rel).map_err(|m| CosyfsError::from_errno(libc::EINVAL, m))?;
    let rootfd = open_root_dir(root)?;
    let c = cstr(rel)?;

    let r = unsafe { libc::unlinkat(rootfd, c.as_ptr(), 0) };
    let err = if r < 0 { Some(errno()) } else { None };
    unsafe {
        libc::close(rootfd);
    }

    if let Some(e) = err {
        return Err(CosyfsError::from_errno(e, "unlink failed"));
    }
    Ok(())
}
