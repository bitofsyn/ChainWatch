BEGIN;
ALTER TABLE staking_observations ADD COLUMN state_time TIMESTAMP WITH TIME ZONE;
ALTER TABLE staking_observations ADD COLUMN block_root VARCHAR(66);
ALTER TABLE staking_observations ADD COLUMN slot VARCHAR(20);
ALTER TABLE staking_observations ADD COLUMN epoch VARCHAR(20);
COMMIT;
