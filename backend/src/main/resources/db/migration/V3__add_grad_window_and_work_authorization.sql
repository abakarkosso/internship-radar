-- Graduation window bounds as the first day of the month; NULL means that end is open or unstated.
ALTER TABLE posting ADD COLUMN grad_earliest DATE;
ALTER TABLE posting ADD COLUMN grad_latest DATE;
ALTER TABLE posting ADD COLUMN work_authorization VARCHAR(20) NOT NULL DEFAULT 'UNSPECIFIED';
