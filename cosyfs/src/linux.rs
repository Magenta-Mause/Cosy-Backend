use libc::{
    O_CLOEXEC, O_CREAT, O_DIRECTORY, O_NOFOLLOW, O_PATH, O_RDONLY, O_TRUNC, O_WRONLY, c_int, mode_t,
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

const SYS_OPENAT2: libc::c_long = libc::SYS_openat2;

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

/// Sets the mode and/or owner of `rel` inside `root` without following symlinks or escaping
/// the root. The target is resolved once with openat2 (`RESOLVE_BENEATH | RESOLVE_NO_SYMLINKS`)
/// and every change is applied to the resulting file descriptor, so there is no path re-walk and
/// no TOCTOU window between the security check and the operation.
///
/// `mode` is applied when `Some`. `owner` (uid, gid) is applied when `Some`.
pub fn set_permissions(
    root: &str,
    rel: &str,
    mode: Option<mode_t>,
    owner: Option<(libc::uid_t, libc::gid_t)>,
) -> Result<(), CosyfsError> {
    let rootfd = open_root_dir(root)?;

    // O_PATH yields a handle to the target inode without needing read permission and without
    // following the final component; openat2's RESOLVE_NO_SYMLINKS refuses a symlink in ANY
    // component, so a symlinked path is rejected rather than followed.
    let fd = match open_under_root(rootfd, rel, O_PATH, 0) {
        Ok(fd) => fd,
        Err(e) => {
            unsafe {
                libc::close(rootfd);
            }
            return Err(e);
        }
    };
    unsafe {
        libc::close(rootfd);
    }

    let res = (|| {
        if let Some(m) = mode {
            // fchmod() rejects O_PATH descriptors, so reach the resolved inode through its
            // /proc/self/fd magic link. This still refers to the exact fd we opened above.
            let proc_path = cstr(&format!("/proc/self/fd/{}", fd))?;
            let r = unsafe { libc::fchmodat(libc::AT_FDCWD, proc_path.as_ptr(), m, 0) };
            if r < 0 {
                return Err(CosyfsError::from_errno(errno(), "fchmod failed"));
            }
        }
        if let Some((uid, gid)) = owner {
            // AT_EMPTY_PATH operates on `fd` itself (the already-resolved, non-symlink target).
            let empty = cstr("")?;
            let r = unsafe { libc::fchownat(fd, empty.as_ptr(), uid, gid, libc::AT_EMPTY_PATH) };
            if r < 0 {
                return Err(CosyfsError::from_errno(errno(), "fchown failed"));
            }
        }
        Ok(())
    })();

    unsafe {
        libc::close(fd);
    }
    res
}

/// Creates `rel` (and any missing parents) inside `root`, like `mkdir -p`, without following or
/// creating through symlinks. Each component is created with `mkdirat` and descended into with
/// `openat(O_NOFOLLOW | O_DIRECTORY)` relative to the previous directory's fd, so a pre-existing
/// symlink component fails with ELOOP instead of being traversed. Existing directories are fine.
pub fn mkdirs(root: &str, rel: &str, mode: mode_t) -> Result<(), CosyfsError> {
    validate_rel(rel).map_err(|m| CosyfsError::from_errno(libc::EINVAL, m))?;

    let mut curfd = open_root_dir(root)?;

    for part in rel.split('/') {
        if part.is_empty() || part == "." {
            continue;
        }
        // ".." is already rejected by validate_rel above.

        let c = match cstr(part) {
            Ok(c) => c,
            Err(e) => {
                unsafe {
                    libc::close(curfd);
                }
                return Err(e);
            }
        };

        // Create the component if it is missing; an existing entry (EEXIST) is fine.
        let r = unsafe { libc::mkdirat(curfd, c.as_ptr(), mode) };
        if r < 0 {
            let e = errno();
            if e != libc::EEXIST {
                unsafe {
                    libc::close(curfd);
                }
                return Err(CosyfsError::from_errno(e, "mkdirat failed"));
            }
        }

        // Descend without following symlinks: O_NOFOLLOW makes a symlinked component fail with
        // ELOOP, and O_DIRECTORY makes a non-directory fail with ENOTDIR.
        let nextfd = unsafe {
            libc::openat(
                curfd,
                c.as_ptr(),
                O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC,
            )
        };
        unsafe {
            libc::close(curfd);
        }
        if nextfd < 0 {
            return Err(CosyfsError::from_errno(errno(), "openat (descend) failed"));
        }
        curfd = nextfd;
    }

    unsafe {
        libc::close(curfd);
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
