INSERT INTO public.pending_requests (
    hearing_id, message, message_type, status, incident_flag, version_number, last_tried_date_time, submitted_date_time, retry_count, deployment_id)
VALUES (2000000001, 'Should be this one', 'REQUEST_HEARING', 'PROCESSING', false, 1, '2023-10-01 10:00:00', '2023-10-01 10:00:00', 1, 'depId01');
