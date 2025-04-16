DELETE FROM pending_requests;
ALTER SEQUENCE public.pending_requests_id_seq RESTART WITH 1;
