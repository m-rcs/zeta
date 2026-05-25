// Zed mobile (iOS) FFI header.
//
// Declares the C-compatible symbols exposed by the `zed_mobile` Rust static
// library and the supporting `gpui_ios_*` lifecycle hooks from `gpui_mobile`
// that `main.m` calls into.

#ifndef ZED_MOBILE_H
#define ZED_MOBILE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// ─── zed_mobile exports ─────────────────────────────────────────────────────

/// Register the Zed mobile root view with the GPUI iOS platform.
///
/// Must be called before `zed_mobile_run()`. Sets the callback that the GPUI
/// run loop invokes to construct the initial window.
void zed_mobile_register(void);

/// Start the GPUI iOS run loop. Does not return.
void zed_mobile_run(void);

// ─── gpui_mobile lifecycle hooks (defined in gpui_mobile::ios::ffi) ─────────

void* gpui_ios_get_window(void);
void  gpui_ios_request_frame(void* window_ptr);
void  gpui_ios_will_enter_foreground(void* app_ptr);
void  gpui_ios_did_become_active(void* app_ptr);
void  gpui_ios_will_resign_active(void* app_ptr);
void  gpui_ios_did_enter_background(void* app_ptr);
void  gpui_ios_will_terminate(void* app_ptr);
void  gpui_ios_handle_open_url(void* url_ptr);

#ifdef __cplusplus
}
#endif

#endif // ZED_MOBILE_H
