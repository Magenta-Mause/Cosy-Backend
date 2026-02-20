use libc::{c_char, c_int, size_t};
use std::{ffi::CString, ptr};

#[repr(C)]
pub struct CosyfsError {
    pub code: c_int,
    pub message: *mut c_char,
}

impl CosyfsError {
    pub fn ok() -> Self {
        Self {
            code: 0,
            message: ptr::null_mut(),
        }
    }
    pub fn from_errno(code: c_int, msg: &str) -> Self {
        let s = CString::new(msg).unwrap_or_else(|_| CString::new("error").unwrap());
        Self {
            code,
            message: s.into_raw(),
        }
    }
}

/// Frees a C string that was allocated by this library and returned to the caller.
///
/// This is intended to be used to free `CosyfsError.message` (and any other
/// `*mut c_char` returned by cosyfs) that was allocated via `CString::into_raw`.
///
/// # Safety
/// - `s` must either be NULL or a pointer previously returned by this library
///   as an owned C string (i.e., allocated with `CString::into_raw`).
/// - The pointer must be freed **exactly once**. Passing a pointer that was
///   already freed, was not allocated by this library, or was not allocated
///   via `CString::into_raw` is undefined behavior.
/// - `s` must point to a valid NUL-terminated string allocated by Rust.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn cosyfs_free_cstring(s: *mut c_char) {
    if s.is_null() {
        return;
    }
    unsafe { drop(CString::from_raw(s)) };
}

/// Frees a byte buffer that was allocated by this library and returned to the caller.
///
/// This is intended to be used to free buffers returned by `cosyfs_read_file`.
///
/// # Safety
/// - `p` must either be NULL or a pointer previously returned by this library
///   as an owned buffer.
/// - `len` must be exactly the length that was returned alongside `p`.
/// - The buffer must be freed **exactly once**. Passing a pointer/length pair
///   that was already freed, was not allocated by this library, or uses the
///   wrong `len` is undefined behavior.
/// - The memory must have been allocated as a `Vec<u8>` and leaked via
///   `Vec::as_mut_ptr` + `mem::forget` (as done by `cosyfs_read_file`).
#[unsafe(no_mangle)]
pub unsafe extern "C" fn cosyfs_free_buf(p: *mut u8, len: size_t) {
    if p.is_null() {
        return;
    }
    unsafe { drop(Vec::from_raw_parts(p, len, len)) };
}
