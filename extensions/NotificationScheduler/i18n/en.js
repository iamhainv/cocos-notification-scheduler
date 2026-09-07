module.exports = {
    title: 'Notification Scheduler — local notifications (UNUserNotificationCenter / AlarmManager)',
    description: 'Notification Scheduler — schedules local notifications via UNUserNotificationCenter (iOS) and AlarmManager / NotificationCompat (Android).',

    enableNotification: {
        title: 'Enable Notification Scheduler',
        tip: 'Enable or disable the Notification Scheduler module. When enabled, the native notification integration files are copied into the built project.',
    },

    overwriteLibrary: {
        title: 'Force overwrite the Notification Scheduler library',
        tip: 'Force overwrite the files in the built project from the extension\'s template.',
    },
};
