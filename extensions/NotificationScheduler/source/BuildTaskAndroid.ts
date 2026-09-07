/*
 NotificationScheduler — Local notification scheduling extension for Cocos Creator 3.8.8
 Android: AlarmManager / NotificationCompat  |  iOS: UNUserNotificationCenter

 MIT License
*/
import { IBuildResult } from '../@types';
import { PACKAGE_NAME } from './builder';
import * as fse from 'fs-extra';
import { ITaskOptions } from './hooks';
import { NotificationOption } from './NotificationOptions';
import { AndroidConstants } from './AndroidConstants';
import { appendDestContentToSrcFileIfNo, deleteDestContentInSrcFile } from './Util';

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
export class BuildTaskAndroid {

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
    private handleGradleFiles(options: ITaskOptions, buildResult: IBuildResult): void {
        console.log(TAG, 'handleGradleFiles', `Build path is: ${buildResult.dest}`);

        const notifOption = options.packages[PACKAGE_NAME] as NotificationOption;
        const enableNotification = notifOption.enableNotification ?? true;

        // build.gradle: the app module lives in the PERSISTENT native project (3.8 layout: settings.gradle maps :app -> native/engine/android/app).
        const srcGradle = `${Editor.Project.path}/native/engine/android/app/build.gradle`;
        const destGradle = AndroidConstants.NotificationTemplateGradlePath;

        // settings.gradle: lives at the project root inside the built output.
        const srcSetting = `${buildResult.dest}/proj/${AndroidConstants.SettingGradle}`;
        const destSetting = AndroidConstants.NotificationTemplateSettingGradle;

        if (enableNotification) {
            appendDestContentToSrcFileIfNo(srcGradle, destGradle);
            appendDestContentToSrcFileIfNo(srcSetting, destSetting);
        } else {
            deleteDestContentInSrcFile(srcGradle, destGradle);
            deleteDestContentInSrcFile(srcSetting, destSetting);
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
    private copyLibraryDirectory(options: ITaskOptions, buildResult: IBuildResult): void {
        console.log(TAG, 'copyLibraryDirectory', `Build path is: ${buildResult.dest}`);

        const notifOption = options.packages[PACKAGE_NAME] as NotificationOption;
        const { enableNotification, overwriteLibrary } = notifOption;

        if (!enableNotification) {
            console.log(TAG, 'copyLibraryDirectory', 'skipped — enableNotification is false');
            return;
        }

        const srcLib = AndroidConstants.NotificationLibSrcPath;
        const destLib = `${buildResult.dest}${AndroidConstants.NotificationLibDestPath}`;
        console.log(TAG, 'copyLibraryDirectory', `from ${srcLib} to ${destLib}`);
        fse.copySync(srcLib, destLib, { recursive: true, overwrite: overwriteLibrary ?? true });
        console.log(TAG, 'copyLibraryDirectory', 'done');
    }

    /**
     * @en
     * Run all post-build tasks in sequence.
     *
     * @param options      Build task options.
     * @param buildResult  Cocos build result.
     */
    public executePostBuildTasks(options: ITaskOptions, buildResult: IBuildResult): void {
        this.handleGradleFiles(options, buildResult);
        this.copyLibraryDirectory(options, buildResult);
        console.log(TAG, 'executePostBuildTasks', 'all tasks done.');
    }
}

/**
 * @en
 * Singleton build-task instance consumed by `hooks.ts`.
 */
export const buildTaskAndroid = {
    android: new BuildTaskAndroid(),
};
