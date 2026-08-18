ALTER TABLE showcase_event
    DROP CONSTRAINT IF EXISTS chk_showcase_event_type;

ALTER TABLE showcase_event
    ADD CONSTRAINT chk_showcase_event_type CHECK (
        event_type IN (
            'VIEW', 'VIDEO_START', 'VIDEO_COMPLETE',
            'RESERVE_CLICK', 'BOOKING_CONFIRMED', 'SHARE'
        )
    );