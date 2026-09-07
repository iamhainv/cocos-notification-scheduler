/**
 * NotificationScheduler extension — public barrel export.
 *
 * Import via the extension alias:
 *   import { notificationScheduler, NotificationImportance, NotificationData } from 'db://notification-scheduler/index';
 */

export { notificationScheduler, NotificationClient } from './NotificationClient';
export type {
    NotificationChannelOptions,
    NotificationData,
    NotificationError,
} from './Types';
export { NotificationImportance } from './Types';
