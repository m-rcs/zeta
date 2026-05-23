//! Zed mobile app library.
//!
//! Statically linked into the iOS and Android app shells under `mobile/`.

#![cfg_attr(not(any(target_os = "ios", target_os = "android")), allow(dead_code))]

#[cfg(any(target_os = "ios", target_os = "android"))]
use gpui::{App, Render, Window, prelude::*, rgb};

#[cfg(any(target_os = "ios", target_os = "android"))]
struct RootView;

#[cfg(any(target_os = "ios", target_os = "android"))]
impl Render for RootView {
    fn render(
        &mut self,
        _window: &mut Window,
        _cx: &mut gpui::Context<Self>,
    ) -> impl IntoElement {
        gpui::div()
            .size_full()
            .bg(rgb(0x1e1e2e))
            .flex()
            .items_center()
            .justify_center()
            .child(
                gpui::div()
                    .text_color(rgb(0xcdd6f4))
                    .text_xl()
                    .child("Zed Mobile"),
            )
    }
}

#[cfg(any(target_os = "ios", target_os = "android"))]
fn open_main_window(cx: &mut App) {
    log::info!("zed_mobile: opening main window");
    match cx.open_window(
        gpui::WindowOptions {
            window_bounds: None,
            ..Default::default()
        },
        |_, cx| cx.new(|_| RootView),
    ) {
        Ok(_) => log::info!("zed_mobile: window opened"),
        Err(error) => log::error!("zed_mobile: failed to open window: {error:#}"),
    }
    cx.activate(true);
}

// ─── iOS entry point ────────────────────────────────────────────────────────

#[cfg(target_os = "ios")]
mod ios {
    use super::*;

    struct NsLogLogger;

    impl log::Log for NsLogLogger {
        fn enabled(&self, _metadata: &log::Metadata) -> bool {
            true
        }

        fn log(&self, record: &log::Record) {
            let line = format!(
                "[{}] {}: {}",
                record.level(),
                record.target(),
                record.args()
            );
            nslog(&line);
        }

        fn flush(&self) {}
    }

    fn nslog(message: &str) {
        use objc2::runtime::AnyObject;
        use objc2::{class, msg_send};
        unsafe {
            unsafe extern "C" {
                fn NSLog(fmt: *mut AnyObject, ...);
            }
            let Ok(c_message) = std::ffi::CString::new(message) else {
                return;
            };
            let Ok(c_format) = std::ffi::CString::new("%@") else {
                return;
            };
            let ns_message: *mut AnyObject = msg_send![class!(NSString), alloc];
            let ns_message: *mut AnyObject =
                msg_send![ns_message, initWithUTF8String: c_message.as_ptr()];
            let ns_format: *mut AnyObject = msg_send![class!(NSString), alloc];
            let ns_format: *mut AnyObject =
                msg_send![ns_format, initWithUTF8String: c_format.as_ptr()];
            NSLog(ns_format, ns_message);
            let _: () = msg_send![ns_message, release];
            let _: () = msg_send![ns_format, release];
        }
    }

    static LOGGER: NsLogLogger = NsLogLogger;

    /// Register the Zed mobile root view with the GPUI iOS platform.
    ///
    /// Called from `main.m` before `zed_mobile_run()`.
    #[unsafe(no_mangle)]
    pub extern "C" fn zed_mobile_register() {
        let _ = log::set_logger(&LOGGER).map(|()| log::set_max_level(log::LevelFilter::Info));

        std::panic::set_hook(Box::new(|info| {
            nslog(&format!("zed_mobile PANIC: {info}"));
        }));

        gpui_mobile::ios::ffi::set_app_callback(Box::new(|cx: &mut App| {
            open_main_window(cx);
        }));
    }

    /// Start the GPUI run loop. Must be called after `zed_mobile_register()`.
    #[unsafe(no_mangle)]
    pub extern "C" fn zed_mobile_run() {
        gpui_mobile::ios::ffi::run_app();
    }
}

// ─── Android entry point ────────────────────────────────────────────────────

#[cfg(target_os = "android")]
mod android {
    use super::*;
    use gpui::Application;
    use gpui_mobile::android::jni;

    #[unsafe(no_mangle)]
    fn android_main(app: android_activity::AndroidApp) {
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(log::LevelFilter::Info)
                .with_tag("zed_mobile"),
        );
        jni::install_panic_hook();
        log::info!("zed_mobile: android_main entered");

        let _platform = jni::init_platform(&app);
        let Some(shared) = jni::shared_platform() else {
            log::error!("zed_mobile: shared_platform() returned None");
            return;
        };

        Application::with_platform(shared.into_rc()).run(|cx: &mut App| {
            open_main_window(cx);
        });
    }
}
