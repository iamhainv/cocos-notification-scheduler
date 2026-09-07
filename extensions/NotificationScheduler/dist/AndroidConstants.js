"use strict";
/*
 NotificationScheduler — Local notification scheduling extension for Cocos Creator 3.8.8
 Android: AlarmManager / NotificationCompat  |  iOS: UNUserNotificationCenter

 MIT License
*/
Object.defineProperty(exports, "__esModule", { value: true });
exports.AndroidConstants = void 0;
/**
 * @en
 * Compile-time path constants for the Android build task.
 * All paths are derived from `Editor.Project.path` or `__dirname` at runtime —
 * no hardcoded absolute paths.
 */
class AndroidConstants {
}
exports.AndroidConstants = AndroidConstants;
/**
 * @en The native android engine path inside the Cocos project. */
AndroidConstants.NativePath = `${Editor.Project.path}/native/engine/android`;
/**
 * @en The project-level build.gradle that receives the notification dependency snippet. */
AndroidConstants.AppBuildGradle = `${AndroidConstants.NativePath}/app/build.gradle`;
/**
 * @en The template directory of this extension on disk. */
AndroidConstants.NotificationTemplatePath = `${__dirname}/../template/android`;
/**
 * @en
 * The build.gradle snippet template that is appended to (or removed from)
 * the built project's build.gradle. */
AndroidConstants.NotificationTemplateGradlePath = `${AndroidConstants.NotificationTemplatePath}/build.gradle`;
/**
 * @en The name of the Android native library module. */
AndroidConstants.NotificationLibName = 'libnotification';
/**
 * @en Source path of the libnotification module inside this extension's template. */
AndroidConstants.NotificationLibSrcPath = `${AndroidConstants.NotificationTemplatePath}/${AndroidConstants.NotificationLibName}`;
/**
 * @en
 * Relative destination path (appended to `buildResult.dest`) where the
 * libnotification module is copied inside the built project. */
AndroidConstants.NotificationLibDestPath = `/proj/${AndroidConstants.NotificationLibName}`;
/**
 * @en The filename of the settings.gradle used by the Android project. */
AndroidConstants.SettingGradle = 'settings.gradle';
/**
 * @en
 * The settings.gradle snippet template (adds `include ':libnotification'`) stored
 * inside this extension's template directory. */
AndroidConstants.NotificationTemplateSettingGradle = `${AndroidConstants.NotificationTemplatePath}/${AndroidConstants.SettingGradle}`;
