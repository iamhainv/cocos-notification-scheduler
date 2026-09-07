/*
 NotificationScheduler — Local notification scheduling extension for Cocos Creator 3.8.8
 Android: AlarmManager / NotificationCompat  |  iOS: UNUserNotificationCenter

 MIT License
*/
import { IBuildTaskOption, BuildHook, IBuildResult } from '../@types';
import { buildTaskAndroid } from './BuildTaskAndroid';
import { buildTaskiOS } from './BuildTaskiOS';

/** @en Package-level options shape coming from the build panel. */
interface INotificationPanelOptions {
    enableNotification: boolean;
    overwriteLibrary: boolean;
}

const PACKAGE_NAME = 'notification-scheduler';

/** @en Typed build-task options for the NotificationScheduler extension. */
export interface ITaskOptions extends IBuildTaskOption {
    packages: {
        'notification-scheduler': INotificationPanelOptions;
    };
}

/** @en When true, hook errors abort the build immediately. */
export const throwError: BuildHook.throwError = true;

/** @en Pre-build hook — runs once before compilation begins. */
export const load: BuildHook.load = async function () {
    console.log(`[${PACKAGE_NAME}] Load NotificationScheduler plugin in builder.`);
};

/** @en Cleanup hook — runs when the plugin is unloaded. */
export const unload: BuildHook.unload = async function () {
    console.log(`[${PACKAGE_NAME}] Unload NotificationScheduler plugin in builder.`);
};

/**
 * @en
 * Android post-build hook.
 * Injects Gradle snippets and copies the libnotification native library into
 * the built Android project.
 */
export const onAfterBuild: BuildHook.onAfterBuild = async function (
    options: ITaskOptions,
    result: IBuildResult,
) {
    console.log(`[${PACKAGE_NAME}] onAfterBuild — platform: ${options.platform}`);
    if (options.platform === 'android') {
        buildTaskAndroid.android.executePostBuildTasks(options, result);
    }
};

/**
 * @en
 * iOS post-build hook.
 * Copies CMake integration files and the notification bridge directory into
 * the built iOS project.
 */
export const onAfterCompressSettings: BuildHook.onAfterCompressSettings = async function (
    options: ITaskOptions,
    result: IBuildResult,
) {
    console.log(`[${PACKAGE_NAME}] onAfterCompressSettings — platform: ${options.platform}`);
    if (options.platform === 'ios') {
        buildTaskiOS.ios.executePostBuildTasks(options, result);
    }
};

/** @en Fires when the build encounters an error. */
export const onError: BuildHook.onError = async function (_options, _result) {
    console.warn(`[${PACKAGE_NAME}] build error encountered.`);
};

/** @en Fires before native make step. */
export const onBeforeMake: BuildHook.onBeforeMake = async function (root, options) {
    console.log(`[${PACKAGE_NAME}] onBeforeMake: root=${root}`);
};

/** @en Fires after native make step. */
export const onAfterMake: BuildHook.onAfterMake = async function (root, options) {
    console.log(`[${PACKAGE_NAME}] onAfterMake: root=${root}`);
};
