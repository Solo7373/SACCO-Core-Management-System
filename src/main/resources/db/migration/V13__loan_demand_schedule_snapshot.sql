-- Demand notices keep a frozen schedule snapshot so a reprint always shows the
-- arrears position that existed when the notice was issued.

ALTER TABLE loan_demands ADD COLUMN schedule_snapshot TEXT;
