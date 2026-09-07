/**
 * NotificationSchedulerTest — runtime demo for the Cocos NotificationScheduler
 * extension.
 *
 * Builds its whole UI in code (no scene-baked buttons) so the example is
 * fully readable in a single file. Attach this component to any Node in a
 * scene with a UI camera (see assets/scene/main.scene) and it will lay out
 * five buttons:
 *
 *   - Schedule in 5s     — notificationScheduler.schedule() with delaySeconds=5
 *   - Schedule Repeating  — schedule() with intervalSeconds=60 (iOS floor)
 *   - Cancel One          — cancel() the most recently scheduled id
 *   - Cancel All          — cancelAll()
 *   - List Pending        — show notifications scheduled by *this* demo session
 *
 * On startup (native only) the demo:
 *   1. Calls notificationScheduler.initialize().
 *   2. Creates a "demo" notification channel (Android; no-op on iOS).
 *   3. Requests the POST_NOTIFICATIONS runtime permission if not already
 *      granted (Android 13+ only; always granted below API 33 and on iOS
 *      pre-authorization — the actual UNUserNotificationCenter authorization
 *      prompt is triggered the first time `schedule()` succeeds on iOS,
 *      see doc/en/ios/index.md).
 *   4. Subscribes to `notificationOpened` / `notificationDismissed` so the
 *      status label reflects taps and swipe-dismissals while the app is
 *      running.
 *
 * On web preview / editor (non-native), the extension's bridge already
 * rejects every call with { code: 'E_NOT_SUPPORTED' } — this component just
 * catches that and shows a friendly status message instead of crashing.
 *
 * Known limitation reflected here: the native layer does not expose a
 * "list all pending notifications" query (see extensions' doc "Known
 * Limitations"). "List Pending" therefore reports what *this demo session*
 * has scheduled and not yet cancelled — not a authoritative OS-level query.
 * A real game should keep its own bookkeeping the same way if it needs this.
 */

import { _decorator, Component, Node, Label, Graphics, Color, UITransform, Vec3, sys } from 'cc';
import { notificationScheduler, NotificationImportance } from 'db://notification-scheduler/index';
import type { NotificationData, NotificationError } from 'db://notification-scheduler/index';

const { ccclass } = _decorator;

const TAG = '[NotificationSchedulerTest]';
const CHANNEL_ID = 'demo';
const REPEAT_INTERVAL_SECONDS = 60; // iOS silently clamps below this anyway.

interface _TrackedNotification {
    id: number;
    label: string;
    scheduledAt: number;
    delaySeconds: number;
    intervalSeconds?: number;
}

@ccclass('NotificationSchedulerTest')
export class NotificationSchedulerTest extends Component {

    private _statusLabel: Label | null = null;
    private _nextId = 1;
    private _pending: _TrackedNotification[] = [];
    private _ready = false;

    onLoad() {
        this._buildUI();
        if (sys.isNative) {
            void this._initNotifications();
        }
    }

    // -----------------------------------------------------------------
    // UI construction (no scene-baked prefabs — everything built in code)
    // -----------------------------------------------------------------

    private _buildUI(): void {
        const root = this.node;
        const rootUi = root.getComponent(UITransform) ?? root.addComponent(UITransform);
        rootUi.setContentSize(640, 720);

        this._addLabel(root, 'Cocos NotificationScheduler Demo', 0, 310, 24, true);

        this._statusLabel = this._addLabel(
            root,
            sys.isNative
                ? 'Initializing...'
                : 'Web/editor preview — native notifications are disabled here.\nBuild to Android/iOS to test.',
            0, 220, 18, false,
        );

        this._addButton(root, 'Schedule in 5s', 135, () => this._onScheduleOnce());
        this._addButton(root, 'Schedule Repeating', 65, () => this._onScheduleRepeating());
        this._addButton(root, 'Cancel One', -5, () => this._onCancelOne());
        this._addButton(root, 'Cancel All', -75, () => this._onCancelAll());
        this._addButton(root, 'List Pending', -145, () => this._onListPending());
    }

    private _addLabel(parent: Node, text: string, x: number, y: number, fontSize: number, bold: boolean): Label {
        const node = new Node('Label');
        parent.addChild(node);
        node.setPosition(new Vec3(x, y, 0));
        const ui = node.addComponent(UITransform);
        ui.setContentSize(600, 100);
        const label = node.addComponent(Label);
        label.string = text;
        label.fontSize = fontSize;
        label.lineHeight = fontSize + 6;
        label.isBold = bold;
        label.horizontalAlign = Label.HorizontalAlign.CENTER;
        label.verticalAlign = Label.VerticalAlign.CENTER;
        label.overflow = Label.Overflow.RESIZE_HEIGHT;
        label.color = new Color(255, 255, 255, 255);
        return label;
    }

    private _addButton(parent: Node, text: string, y: number, onClick: () => void): Node {
        const node = new Node(`Button_${text.replace(/\s+/g, '')}`);
        parent.addChild(node);
        node.setPosition(new Vec3(0, y, 0));

        const ui = node.addComponent(UITransform);
        ui.setContentSize(320, 56);

        const graphics = node.addComponent(Graphics);
        graphics.fillColor = new Color(43, 108, 176, 255);
        graphics.roundRect(-160, -28, 320, 56, 12);
        graphics.fill();

        const labelNode = new Node('Label');
        node.addChild(labelNode);
        const labelUi = labelNode.addComponent(UITransform);
        labelUi.setContentSize(320, 56);
        const label = labelNode.addComponent(Label);
        label.string = text;
        label.fontSize = 20;
        label.horizontalAlign = Label.HorizontalAlign.CENTER;
        label.verticalAlign = Label.VerticalAlign.CENTER;
        label.color = new Color(255, 255, 255, 255);

        node.on(Node.EventType.TOUCH_END, () => onClick(), this);

        return node;
    }

    private _setStatus(text: string): void {
        if (this._statusLabel) {
            this._statusLabel.string = text;
        }
        console.log(TAG, text);
    }

    // -----------------------------------------------------------------
    // Startup — initialize, create channel, request permission, subscribe
    // -----------------------------------------------------------------

    private async _initNotifications(): Promise<void> {
        try {
            await notificationScheduler.initialize();

            // Android: required before scheduling. No-op on iOS.
            await notificationScheduler.createNotificationChannel({
                channelId: CHANNEL_ID,
                channelName: 'Demo Reminders',
                channelDescription: 'Notifications scheduled from the NotificationScheduler demo scene.',
                importance: NotificationImportance.DEFAULT,
                badgeEnabled: true,
            });

            notificationScheduler.onNotificationOpened((data: NotificationData) => {
                this._setStatus(`Opened notification id=${data.notificationId}.`);
            });
            notificationScheduler.onNotificationDismissed((data: NotificationData) => {
                this._setStatus(`Dismissed notification id=${data.notificationId} (Android only).`);
            });

            // Request the POST_NOTIFICATIONS runtime permission up front
            // (Android 13+ only; resolves immediately elsewhere). The actual
            // iOS UNUserNotificationCenter authorization prompt is triggered
            // implicitly by the OS the first time a notification is
            // scheduled — see doc/en/ios/index.md.
            const permResult = await notificationScheduler.requestPostNotificationsPermission();

            this._ready = true;
            this._setStatus(
                permResult.granted
                    ? 'Ready. Tap a button to schedule or manage notifications.'
                    : 'Notifications permission denied — scheduled notifications will not be shown. '
                        + 'Use system settings to grant it, then re-open the app.',
            );
        } catch (e) {
            this._reportError('Initialize', e as NotificationError);
        }
    }

    // -----------------------------------------------------------------
    // Button handlers
    // -----------------------------------------------------------------

    private async _onScheduleOnce(): Promise<void> {
        const id = this._nextId++;
        this._setStatus(`Scheduling notification id=${id} (fires in 5s)...`);
        try {
            await notificationScheduler.schedule({
                notificationId: id,
                channelId: CHANNEL_ID,
                title: 'Cocos NotificationScheduler Demo',
                content: `One-shot notification #${id}, scheduled 5s ago.`,
                delaySeconds: 5,
            });
            this._pending.push({
                id,
                label: `#${id} one-shot (5s)`,
                scheduledAt: Date.now(),
                delaySeconds: 5,
            });
            this._setStatus(`Scheduled id=${id}. It will fire in ~5 seconds.`);
        } catch (e) {
            this._reportError('Schedule', e as NotificationError);
        }
    }

    private async _onScheduleRepeating(): Promise<void> {
        const id = this._nextId++;
        this._setStatus(`Scheduling repeating notification id=${id}...`);
        try {
            await notificationScheduler.schedule({
                notificationId: id,
                channelId: CHANNEL_ID,
                title: 'Cocos NotificationScheduler Demo (repeating)',
                content: `Repeating notification #${id}, every ${REPEAT_INTERVAL_SECONDS}s.`,
                delaySeconds: 10,
                intervalSeconds: REPEAT_INTERVAL_SECONDS,
            });
            this._pending.push({
                id,
                label: `#${id} repeating (every ${REPEAT_INTERVAL_SECONDS}s)`,
                scheduledAt: Date.now(),
                delaySeconds: 10,
                intervalSeconds: REPEAT_INTERVAL_SECONDS,
            });
            this._setStatus(`Scheduled repeating id=${id}. First fire in ~10s, then every ${REPEAT_INTERVAL_SECONDS}s.`);
        } catch (e) {
            this._reportError('Schedule (repeating)', e as NotificationError);
        }
    }

    private async _onCancelOne(): Promise<void> {
        const last = this._pending.pop();
        if (!last) {
            this._setStatus('Nothing scheduled by this demo session to cancel.');
            return;
        }
        this._setStatus(`Cancelling id=${last.id}...`);
        try {
            await notificationScheduler.cancel(last.id);
            this._setStatus(`Cancelled id=${last.id} (${last.label}).`);
        } catch (e) {
            this._reportError('Cancel', e as NotificationError);
        }
    }

    private async _onCancelAll(): Promise<void> {
        this._setStatus('Cancelling all notifications...');
        try {
            await notificationScheduler.cancelAll();
            this._pending = [];
            this._setStatus('Cancelled all notifications posted by this app.');
        } catch (e) {
            this._reportError('Cancel all', e as NotificationError);
        }
    }

    /**
     * There is no native "list all pending notifications" query in this
     * extension (see doc "Known Limitations"), so this reports what *this
     * demo session* has scheduled and not yet cancelled/cleared — it does
     * not reflect notifications that already fired, or ones scheduled in a
     * previous run of the app.
     */
    private _onListPending(): void {
        if (!sys.isNative) {
            this._setStatus('Native only — build to Android/iOS to try this.');
            return;
        }
        if (this._pending.length === 0) {
            this._setStatus('No notifications tracked as pending in this session.');
            return;
        }
        const lines = this._pending.map((p) => `- ${p.label}`);
        this._setStatus(`Pending (this session, ${this._pending.length}):\n${lines.join('\n')}`);
    }

    private _reportError(what: string, err: NotificationError): void {
        if (err && err.code === 'E_NOT_SUPPORTED') {
            this._setStatus(`${what}: native only — build to Android/iOS to try this.`);
            return;
        }
        const message = (err && err.message) || String(err);
        const code = (err && err.code) || 'E_UNKNOWN';
        this._setStatus(`${what} failed [${code}]: ${message}`);
    }
}
