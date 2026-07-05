mod error;
mod linux;

use crate::error::CosyfsError;
use libc::{c_char, c_int, mode_t, size_t};
use std::io::{self, Write};
use std::{ffi::CStr, ptr};

/// Returns true if tracing is enabled via the `COSYFS_TRACE` environment variable.
fn trace_enabled() -> bool {
    std::env::var_os("COSYFS_TRACE").is_some()
}

/// Writes a trace line to stderr when tracing is enabled.
fn trace(msg: &str) {
    if !trace_enabled() {
        return;
    }
    use std::io::{self, Write};
    let _ = writeln!(io::stderr(), "[cosyfs] {}", msg);
    let _ = io::stderr().flush();
}

/// Writes a key/value style trace line to stderr when tracing is enabled.
fn trace_kv(op: &str, root: &str, rel: &str, extra: &str) {
    if !trace_enabled() {
        return;
    }
    use std::io::{self, Write};
    let _ = writeln!(
        io::stderr(),
        "[cosyfs] op={} root='{}' rel='{}' {}",
        op,
        root,
        rel,
        extra
    );
    let _ = io::stderr().flush();
}

/// Reads a file `rel` inside the directory `root` without allowing path escape (implementation-defined).
///
/// On success:
/// - allocates a buffer on the Rust heap
/// - stores the pointer in `*out_ptr` and the length in `*out_len`
/// - the caller must free it with `cosyfs_free_buf`
///
/// On failure:
/// - returns -1
/// - writes an error into `*err`
/// - sets `*out_ptr = NULL`, `*out_len = 0`
/// # Safety
/// The caller must guarantee:
/// - `root` and `rel` are valid, NUL-terminated C strings for the duration of the call.
/// - `out_ptr`, `out_len`, and `err` are valid writable pointers for the duration of the call.
/// - If the function returns 0, the returned `*out_ptr`/`*out_len` buffer is freed exactly once
///   by calling `cosyfs_free_buf(*out_ptr, *out_len)`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn cosyfs_read_file(
    root: *const c_char,
    rel: *const c_char,
    max_bytes: size_t,
    out_ptr: *mut *mut u8,
    out_len: *mut size_t,
    err: *mut CosyfsError,
) -> c_int {
    trace("enter read_file");

    unsafe {
        if out_ptr.is_null()
            || out_len.is_null()
            || err.is_null()
            || root.is_null()
            || rel.is_null()
        {
            trace("read_file: null pointer arg");
            return -1;
        }
        *err = CosyfsError::ok();
        *out_ptr = ptr::null_mut();
        *out_len = 0;

        let root_s = match CStr::from_ptr(root).to_str() {
            Ok(s) => s,
            Err(_) => {
                trace("read_file: root invalid utf-8");
                *err = CosyfsError::from_errno(libc::EINVAL, "root invalid utf-8");
                return -1;
            }
        };
        let rel_s = match CStr::from_ptr(rel).to_str() {
            Ok(s) => s,
            Err(_) => {
                trace_kv("read", root_s, "<invalid-utf8>", "status=EINVAL");
                *err = CosyfsError::from_errno(libc::EINVAL, "rel invalid utf-8");
                return -1;
            }
        };

        trace_kv("read", root_s, rel_s, &format!("max_bytes={}", max_bytes));

        match linux::read_file(root_s, rel_s, max_bytes) {
            Ok(buf) => {
                let len = buf.len();
                trace_kv("read", root_s, rel_s, &format!("status=OK len={}", len));

                let mut v = buf;
                let p = v.as_mut_ptr();
                std::mem::forget(v);

                *out_ptr = p;
                *out_len = len as size_t;
                0
            }
            Err(e) => {
                trace_kv(
                    "read",
                    root_s,
                    rel_s,
                    &format!("status=ERR errno={}", e.code),
                );
                *err = e;
                -1
            }
        }
    }
}

/// Writes `data` to `rel` inside `root`, truncating or creating the file.
/// `mode` is applied when creating a new file (implementation-defined).
///
/// Returns 0 on success, -1 on failure and writes details into `*err`.
/// # Safety
/// The caller must guarantee:
/// - `root` and `rel` are valid, NUL-terminated C strings for the duration of the call.
/// - `err` is a valid writable pointer for the duration of the call.
/// - If `data_len > 0`, then `data` points to at least `data_len` bytes readable for the duration of the call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn cosyfs_write_file_truncate(
    root: *const c_char,
    rel: *const c_char,
    data: *const u8,
    data_len: size_t,
    mode: mode_t,
    err: *mut CosyfsError,
) -> c_int {
    trace("enter write_file_truncate");

    unsafe {
        if err.is_null() || root.is_null() || rel.is_null() {
            trace("write_file_truncate: null pointer arg");
            return -1;
        }
        *err = CosyfsError::ok();

        let root_s = match CStr::from_ptr(root).to_str() {
            Ok(s) => s,
            Err(_) => {
                trace("write_file_truncate: root invalid utf-8");
                *err = CosyfsError::from_errno(libc::EINVAL, "root invalid utf-8");
                return -1;
            }
        };
        let rel_s = match CStr::from_ptr(rel).to_str() {
            Ok(s) => s,
            Err(_) => {
                trace_kv("write", root_s, "<invalid-utf8>", "status=EINVAL");
                *err = CosyfsError::from_errno(libc::EINVAL, "rel invalid utf-8");
                return -1;
            }
        };

        if data.is_null() && data_len > 0 {
            trace_kv("write", root_s, rel_s, "status=EINVAL data=null");
            *err = CosyfsError::from_errno(libc::EINVAL, "data null");
            return -1;
        }

        trace_kv(
            "write",
            root_s,
            rel_s,
            &format!("len={} mode={:o}", data_len, mode),
        );

        let slice = if data_len == 0 {
            &[][..]
        } else {
            std::slice::from_raw_parts(data, data_len)
        };

        match linux::write_file_truncate(root_s, rel_s, slice, mode) {
            Ok(()) => {
                trace_kv("write", root_s, rel_s, "status=OK");
                0
            }
            Err(e) => {
                trace_kv(
                    "write",
                    root_s,
                    rel_s,
                    &format!("status=ERR errno={}", e.code),
                );
                *err = e;
                -1
            }
        }
    }
}

/// Renames (moves) `old_rel` to `new_rel` inside `root`.
///
/// Returns 0 on success, -1 on failure and writes details into `*err`.
/// # Safety
/// The caller must guarantee:
/// - `root`, `old_rel`, and `new_rel` are valid, NUL-terminated C strings for the duration of the call.
/// - `err` is a valid writable pointer for the duration of the call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn cosyfs_rename(
    root: *const c_char,
    old_rel: *const c_char,
    new_rel: *const c_char,
    err: *mut CosyfsError,
) -> c_int {
    trace("enter rename");

    unsafe {
        if err.is_null() || root.is_null() || old_rel.is_null() || new_rel.is_null() {
            trace("rename: null pointer arg");
            return -1;
        }
        *err = CosyfsError::ok();

        let root_s = match CStr::from_ptr(root).to_str() {
            Ok(s) => s,
            Err(_) => {
                trace("rename: root invalid utf-8");
                *err = CosyfsError::from_errno(libc::EINVAL, "root invalid utf-8");
                return -1;
            }
        };
        let old_s = match CStr::from_ptr(old_rel).to_str() {
            Ok(s) => s,
            Err(_) => {
                trace_kv("rename", root_s, "<old-invalid-utf8>", "status=EINVAL");
                *err = CosyfsError::from_errno(libc::EINVAL, "old invalid utf-8");
                return -1;
            }
        };
        let new_s = match CStr::from_ptr(new_rel).to_str() {
            Ok(s) => s,
            Err(_) => {
                trace_kv("rename", root_s, old_s, "status=EINVAL new-invalid-utf8");
                *err = CosyfsError::from_errno(libc::EINVAL, "new invalid utf-8");
                return -1;
            }
        };

        trace_kv("rename", root_s, old_s, &format!("new='{}'", new_s));

        match linux::rename_path(root_s, old_s, new_s) {
            Ok(()) => {
                trace_kv("rename", root_s, old_s, "status=OK");
                0
            }
            Err(e) => {
                trace_kv(
                    "rename",
                    root_s,
                    old_s,
                    &format!("status=ERR errno={}", e.code),
                );
                *err = e;
                -1
            }
        }
    }
}

/// Deletes (unlinks) the file at `rel` inside `root`.
///
/// Returns 0 on success, -1 on failure and writes details into `*err`.
/// # Safety
/// The caller must guarantee:
/// - `root` and `rel` are valid, NUL-terminated C strings for the duration of the call.
/// - `err` is a valid writable pointer for the duration of the call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn cosyfs_delete_file(
    root: *const c_char,
    rel: *const c_char,
    err: *mut CosyfsError,
) -> c_int {
    trace("enter delete_file");

    unsafe {
        if err.is_null() || root.is_null() || rel.is_null() {
            trace("delete_file: null pointer arg");
            return -1;
        }
        *err = CosyfsError::ok();

        let root_s = match CStr::from_ptr(root).to_str() {
            Ok(s) => s,
            Err(_) => {
                trace("delete_file: root invalid utf-8");
                *err = CosyfsError::from_errno(libc::EINVAL, "root invalid utf-8");
                return -1;
            }
        };
        let rel_s = match CStr::from_ptr(rel).to_str() {
            Ok(s) => s,
            Err(_) => {
                trace_kv("delete", root_s, "<invalid-utf8>", "status=EINVAL");
                *err = CosyfsError::from_errno(libc::EINVAL, "rel invalid utf-8");
                return -1;
            }
        };

        trace_kv("delete", root_s, rel_s, "request=unlink");

        match linux::delete_file(root_s, rel_s) {
            Ok(()) => {
                trace_kv("delete", root_s, rel_s, "status=OK");
                0
            }
            Err(e) => {
                trace_kv(
                    "delete",
                    root_s,
                    rel_s,
                    &format!("status=ERR errno={}", e.code),
                );
                *err = e;
                -1
            }
        }
    }
}

/// Sets the mode and/or owner of `rel` inside `root`, resolving the target with openat2 so that
/// symlinks are never followed and the volume root is never escaped (no TOCTOU).
///
/// `mode < 0` skips the chmod. `uid < 0 || gid < 0` skips the chown.
///
/// Returns 0 on success, -1 on failure and writes details into `*err`.
/// # Safety
/// The caller must guarantee:
/// - `root` and `rel` are valid, NUL-terminated C strings for the duration of the call.
/// - `err` is a valid writable pointer for the duration of the call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn cosyfs_set_permissions(
    root: *const c_char,
    rel: *const c_char,
    mode: c_int,
    uid: c_int,
    gid: c_int,
    err: *mut CosyfsError,
) -> c_int {
    trace("enter set_permissions");

    unsafe {
        if err.is_null() || root.is_null() || rel.is_null() {
            trace("set_permissions: null pointer arg");
            return -1;
        }
        *err = CosyfsError::ok();

        let root_s = match CStr::from_ptr(root).to_str() {
            Ok(s) => s,
            Err(_) => {
                trace("set_permissions: root invalid utf-8");
                *err = CosyfsError::from_errno(libc::EINVAL, "root invalid utf-8");
                return -1;
            }
        };
        let rel_s = match CStr::from_ptr(rel).to_str() {
            Ok(s) => s,
            Err(_) => {
                trace_kv("chmod", root_s, "<invalid-utf8>", "status=EINVAL");
                *err = CosyfsError::from_errno(libc::EINVAL, "rel invalid utf-8");
                return -1;
            }
        };

        let mode_opt = if mode < 0 { None } else { Some(mode as mode_t) };
        let owner_opt = if uid < 0 || gid < 0 {
            None
        } else {
            Some((uid as libc::uid_t, gid as libc::gid_t))
        };

        trace_kv(
            "chmod",
            root_s,
            rel_s,
            &format!("mode={} uid={} gid={}", mode, uid, gid),
        );

        match linux::set_permissions(root_s, rel_s, mode_opt, owner_opt) {
            Ok(()) => {
                trace_kv("chmod", root_s, rel_s, "status=OK");
                0
            }
            Err(e) => {
                trace_kv(
                    "chmod",
                    root_s,
                    rel_s,
                    &format!("status=ERR errno={}", e.code),
                );
                *err = e;
                -1
            }
        }
    }
}

/// Creates `rel` and any missing parent directories inside `root` (like `mkdir -p`) without
/// following or creating through symlinks.
///
/// Returns 0 on success, -1 on failure and writes details into `*err`.
/// # Safety
/// The caller must guarantee:
/// - `root` and `rel` are valid, NUL-terminated C strings for the duration of the call.
/// - `err` is a valid writable pointer for the duration of the call.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn cosyfs_mkdirs(
    root: *const c_char,
    rel: *const c_char,
    mode: mode_t,
    err: *mut CosyfsError,
) -> c_int {
    trace("enter mkdirs");

    unsafe {
        if err.is_null() || root.is_null() || rel.is_null() {
            trace("mkdirs: null pointer arg");
            return -1;
        }
        *err = CosyfsError::ok();

        let root_s = match CStr::from_ptr(root).to_str() {
            Ok(s) => s,
            Err(_) => {
                trace("mkdirs: root invalid utf-8");
                *err = CosyfsError::from_errno(libc::EINVAL, "root invalid utf-8");
                return -1;
            }
        };
        let rel_s = match CStr::from_ptr(rel).to_str() {
            Ok(s) => s,
            Err(_) => {
                trace_kv("mkdirs", root_s, "<invalid-utf8>", "status=EINVAL");
                *err = CosyfsError::from_errno(libc::EINVAL, "rel invalid utf-8");
                return -1;
            }
        };

        trace_kv("mkdirs", root_s, rel_s, &format!("mode={:o}", mode));

        match linux::mkdirs(root_s, rel_s, mode) {
            Ok(()) => {
                trace_kv("mkdirs", root_s, rel_s, "status=OK");
                0
            }
            Err(e) => {
                trace_kv(
                    "mkdirs",
                    root_s,
                    rel_s,
                    &format!("status=ERR errno={}", e.code),
                );
                *err = e;
                -1
            }
        }
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn cosyfs_debug_ping() {
    let _ = writeln!(io::stderr(), "[cosyfs] debug_ping reached");
    let _ = io::stderr().flush();
}

pub use error::{cosyfs_free_buf, cosyfs_free_cstring};
