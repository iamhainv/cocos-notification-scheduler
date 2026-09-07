"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.buildTaskAndroid = exports.BuildTaskAndroid = void 0;
const builder_1 = require("./builder");
const fse = __importStar(require("fs-extra"));
const AndroidConstants_1 = require("./AndroidConstants");
const Util_1 = require("./Util");
const TAG = '[BuildTaskAndroid]';
/**
 * @en
 * Handles all post-build tasks required to integrate the NotificationScheduler
 * native library into a Cocos Creator Android build.
 *
 * Responsibilities:
 * - Inject / remove the notification dependency snippet in `build.gradle`.
 * - Inject / remove the `include ':libnotification'` line in `settings.gradle`.
 * - Copy `template/android/libnotification/` into the built project's `proj/` directory.
 */
class BuildTaskAndroid {
    /**
     * @en
     * Modify the built project's `build.gradle` and `settings.gradle` to add
     * or remove the notification dependency and module reference.
     *
     * When `enableNotification` is true the template snippets are appended (idempotent).
     * When false, any previously-inserted snippets are cleanly removed.
     *
     * @param options      Build task options including notification panel options.
     * @param buildResult  Cocos build result containing the dest path.
     */
    handleGradleFiles(options, buildResult) {
        var _a;
        console.log(TAG, 'handleGradleFiles', `Build path is: ${buildResult.dest}`);
        const notifOption = options.packages[builder_1.PACKAGE_NAME];
        const enableNotification = (_a = notifOption.enableNotification) !== null && _a !== void 0 ? _a : true;
        // build.gradle: the app module lives in the PERSISTENT native project (3.8 layout: settings.gradle maps :app -> native/engine/android/app).
        const srcGradle = `${Editor.Project.path}/native/engine/android/app/build.gradle`;
        const destGradle = AndroidConstants_1.AndroidConstants.NotificationTemplateGradlePath;
        // settings.gradle: lives at the project root inside the built output.
        const srcSetting = `${buildResult.dest}/proj/${AndroidConstants_1.AndroidConstants.SettingGradle}`;
        const destSetting = AndroidConstants_1.AndroidConstants.NotificationTemplateSettingGradle;
        if (enableNotification) {
            (0, Util_1.appendDestContentToSrcFileIfNo)(srcGradle, destGradle);
            (0, Util_1.appendDestContentToSrcFileIfNo)(srcSetting, destSetting);
        }
        else {
            (0, Util_1.deleteDestContentInSrcFile)(srcGradle, destGradle);
            (0, Util_1.deleteDestContentInSrcFile)(srcSetting, destSetting);
        }
    }
    /**
     * @en
     * Copy the `libnotification` native module from the extension's template directory
     * into the built project's `proj/libnotification` directory.
     *
     * Skipped entirely when `enableNotification` is false.  The `overwriteLibrary` flag
     * controls whether an existing directory is overwritten (useful to preserve
     * manual edits during iterative builds).
     *
     * @param options      Build task options.
     * @param buildResult  Cocos build result containing the dest path.
     */
    copyLibraryDirectory(options, buildResult) {
        console.log(TAG, 'copyLibraryDirectory', `Build path is: ${buildResult.dest}`);
        const notifOption = options.packages[builder_1.PACKAGE_NAME];
        const { enableNotification, overwriteLibrary } = notifOption;
        if (!enableNotification) {
            console.log(TAG, 'copyLibraryDirectory', 'skipped — enableNotification is false');
            return;
        }
        const srcLib = AndroidConstants_1.AndroidConstants.NotificationLibSrcPath;
        const destLib = `${buildResult.dest}${AndroidConstants_1.AndroidConstants.NotificationLibDestPath}`;
        console.log(TAG, 'copyLibraryDirectory', `from ${srcLib} to ${destLib}`);
        fse.copySync(srcLib, destLib, { recursive: true, overwrite: overwriteLibrary !== null && overwriteLibrary !== void 0 ? overwriteLibrary : true });
        console.log(TAG, 'copyLibraryDirectory', 'done');
    }
    /**
     * @en
     * Run all post-build tasks in sequence.
     *
     * @param options      Build task options.
     * @param buildResult  Cocos build result.
     */
    executePostBuildTasks(options, buildResult) {
        this.handleGradleFiles(options, buildResult);
        this.copyLibraryDirectory(options, buildResult);
        console.log(TAG, 'executePostBuildTasks', 'all tasks done.');
    }
}
exports.BuildTaskAndroid = BuildTaskAndroid;
/**
 * @en
 * Singleton build-task instance consumed by `hooks.ts`.
 */
exports.buildTaskAndroid = {
    android: new BuildTaskAndroid(),
};
