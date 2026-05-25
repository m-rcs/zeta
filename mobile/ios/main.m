// Zed mobile (iOS) ObjC entry point.
//
// Hands off to the `zed_mobile` Rust static library which drives all rendering
// through GPUI's Metal backend. UIKit only owns the app lifecycle and the
// `CADisplayLink` that pumps frames.

#import <UIKit/UIKit.h>
#import "zed_mobile.h"

@interface ZedAppDelegate : UIResponder <UIApplicationDelegate>
@property (nonatomic, assign) void *gpuiWindow;
@property (nonatomic, strong) CADisplayLink *displayLink;
@end

@implementation ZedAppDelegate

- (BOOL)application:(UIApplication *)application
    didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    NSLog(@"zed_mobile: didFinishLaunching");

    zed_mobile_register();
    zed_mobile_run();

    self.gpuiWindow = gpui_ios_get_window();
    if (self.gpuiWindow) {
        self.displayLink = [CADisplayLink displayLinkWithTarget:self
                                                       selector:@selector(renderFrame)];
        [self.displayLink addToRunLoop:[NSRunLoop mainRunLoop]
                               forMode:NSRunLoopCommonModes];
        NSLog(@"zed_mobile: window=%p, CADisplayLink attached", self.gpuiWindow);
    } else {
        NSLog(@"zed_mobile: no window created");
    }

    return YES;
}

- (void)renderFrame {
    if (self.gpuiWindow) {
        gpui_ios_request_frame(self.gpuiWindow);
    }
}

- (void)applicationWillEnterForeground:(UIApplication *)application {
    gpui_ios_will_enter_foreground(NULL);
    if (!self.displayLink && self.gpuiWindow) {
        self.displayLink = [CADisplayLink displayLinkWithTarget:self
                                                       selector:@selector(renderFrame)];
        [self.displayLink addToRunLoop:[NSRunLoop mainRunLoop]
                               forMode:NSRunLoopCommonModes];
    }
}

- (void)applicationDidBecomeActive:(UIApplication *)application {
    gpui_ios_did_become_active(NULL);
}

- (void)applicationWillResignActive:(UIApplication *)application {
    gpui_ios_will_resign_active(NULL);
}

- (void)applicationDidEnterBackground:(UIApplication *)application {
    gpui_ios_did_enter_background(NULL);
    if (self.displayLink) {
        [self.displayLink invalidate];
        self.displayLink = nil;
    }
}

- (void)applicationWillTerminate:(UIApplication *)application {
    if (self.displayLink) {
        [self.displayLink invalidate];
        self.displayLink = nil;
    }
    gpui_ios_will_terminate(NULL);
}

- (BOOL)application:(UIApplication *)application
            openURL:(NSURL *)url
            options:(NSDictionary<UIApplicationOpenURLOptionsKey, id> *)options {
    NSString *urlString = [url absoluteString];
    gpui_ios_handle_open_url((__bridge void *)urlString);
    return YES;
}

@end

int main(int argc, char *argv[]) {
    @autoreleasepool {
        return UIApplicationMain(argc, argv, nil, NSStringFromClass([ZedAppDelegate class]));
    }
}
