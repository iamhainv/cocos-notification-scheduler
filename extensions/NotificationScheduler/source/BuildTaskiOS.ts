/*
 NotificationScheduler — Local notification scheduling extension for Cocos Creator 3.8.8
 Android: AlarmManager / NotificationCompat  |  iOS: UNUserNotificationCenter

 MIT License
*/

import { IBuildResult } from '../@types';
import { PACKAGE_NAME } from './builder';
import { NotificationOption } from './NotificationOptions';
import * as fse from 'fs-extra';
import { ITaskOptions } from './hooks';

const TAG = '[BuildTaskiOS]';

/**
 * @en
 * Handles all iOS post-build tasks for the NotificationScheduler extension.
 *
 * Responsibilities:
 *  - Copy Pre-notification.cmake / Post-notification.cmake into native/engine/ios so
 *    Cocos' CMake include chain picks them up.
 *  - Copy the notification bridge directory into the built Xcode project's
 *    proj/notification folder so the CMake integration can reference it.
 *  - Honour the `enableNotification` flag: when false, remove the cmake files from
 *    native/engine/ios instead of copying them.
 */
export class BuildTaskiOS {

    /** Absolute path to the extension's iOS template directory. */
    static readonly NotificationTemplatePath = `${__dirname}/../template/ios`;

    /** Absolute path to the engine's native iOS directory (shared across builds). */
    static readonly NativePath = `${Editor.Project.path}/native/engine/ios`;

    /**
     * @en
     * Copy Pre-notification.cmake and Post-notification.cmake into native/engine/ios,
     * and copy the notification bridge directory into the built Xcode project.
     *
     * @param option       Typed build-task options from the build panel.
     * @param buildResult  Build result carrying the destination path.
     */
    copyNotificationResources(option: ITaskOptions, buildResult: IBuildResult): void {
        console.log(TAG, 'copyNotificationResources', 'start');

        const notifOption = option.packages[PACKAGE_NAME] as NotificationOption;
        const overwrite = notifOption.overwriteLibrary;

        // --- CMake integration files → native/engine/ios ---
        const preCmakeSrc  = `${BuildTaskiOS.NotificationTemplatePath}/Pre-notification.cmake`;
        const postCmakeSrc = `${BuildTaskiOS.NotificationTemplatePath}/Post-notification.cmake`;
        const preCmakeDest  = `${BuildTaskiOS.NativePath}/Pre-notification.cmake`;
        const postCmakeDest = `${BuildTaskiOS.NativePath}/Post-notification.cmake`;

        fse.copySync(preCmakeSrc,  preCmakeDest,  { overwrite });
        fse.copySync(postCmakeSrc, postCmakeDest, { overwrite });

        // --- notification bridge directory → <buildDest>/proj/notification ---
        const notifSrc  = `${BuildTaskiOS.NotificationTemplatePath}/notification`;
        const notifDest = `${buildResult.dest}/proj/notification`;
        fse.copySync(notifSrc, notifDest, { recursive: true, overwrite });

        console.log(TAG, 'copyNotificationResources', 'end');
    }

    /**
     * @en
     * Remove the CMake integration files from native/engine/ios.
     *
     * Called when enableNotification is false so that a previously-enabled build does
     * not leave stale cmake includes in the shared native directory.
     */
    removeNotificationCmakeFiles(): void {
        console.log(TAG, 'removeNotificationCmakeFiles', 'start');

        const preCmakeDest  = `${BuildTaskiOS.NativePath}/Pre-notification.cmake`;
        const postCmakeDest = `${BuildTaskiOS.NativePath}/Post-notification.cmake`;

        fse.removeSync(preCmakeDest);
        fse.removeSync(postCmakeDest);

        console.log(TAG, 'removeNotificationCmakeFiles', 'end');
    }

    /**
     * @en
     * Entry point called by the build hook for iOS post-build tasks.
     *
     * @param options      Typed build-task options from the build panel.
     * @param buildResult  Build result carrying the destination path.
     */
    executePostBuildTasks(options: ITaskOptions, buildResult: IBuildResult): void {
        console.log(TAG, 'executePostBuildTasks', 'start');

        const notifOption = options.packages[PACKAGE_NAME] as NotificationOption;
        const enableNotification = notifOption.enableNotification;

        if (enableNotification) {
            this.copyNotificationResources(options, buildResult);
        } else {
            console.log(TAG, 'executePostBuildTasks',
                'enableNotification is false — removing cmake files');
            this.removeNotificationCmakeFiles();
        }

        console.log(TAG, 'executePostBuildTasks', 'all tasks done.');
    }
}

/**
 * @en
 * The global instance of the iOS build task.
 * Exported as `buildTaskiOS.ios` to match the shape hooks.ts expects.
 */
export const buildTaskiOS = {
    ios: new BuildTaskiOS(),
};
