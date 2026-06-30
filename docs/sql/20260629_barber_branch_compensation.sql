-- Comision o sueldo del profesional por sede.
CREATE TABLE IF NOT EXISTS barber_branch_compensation (
    compensation_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(tenant_id),
    branch_id BIGINT NOT NULL REFERENCES branch(branch_id),
    barber_user_id BIGINT NOT NULL REFERENCES app_user(user_id),
    salary_mode BOOLEAN NOT NULL DEFAULT FALSE,
    commission_percentage NUMERIC(5,2),
    salary_frequency VARCHAR(20),
    fixed_salary_amount NUMERIC(12,2),
    salary_start_date DATE,
    CONSTRAINT uk_barber_branch_compensation UNIQUE (tenant_id, branch_id, barber_user_id),
    CONSTRAINT ck_barber_branch_compensation_values CHECK (
        (salary_mode = TRUE AND fixed_salary_amount > 0 AND salary_frequency IS NOT NULL)
        OR
        (salary_mode = FALSE AND commission_percentage > 0 AND commission_percentage <= 100)
    )
);

CREATE INDEX IF NOT EXISTS idx_barber_branch_compensation_lookup
    ON barber_branch_compensation (tenant_id, branch_id, barber_user_id);

-- Replica la configuracion global actual en cada sede asignada al profesional.
INSERT INTO barber_branch_compensation (
    tenant_id, branch_id, barber_user_id, salary_mode,
    commission_percentage, salary_frequency, fixed_salary_amount, salary_start_date
)
SELECT DISTINCT
    utr.tenant_id,
    utr.branch_id,
    utr.user_id,
    COALESCE(u.salary_mode, FALSE),
    CASE WHEN COALESCE(u.salary_mode, FALSE) THEN NULL ELSE u.commission_percentage END,
    CASE WHEN COALESCE(u.salary_mode, FALSE) THEN u.salary_frequency ELSE NULL END,
    CASE WHEN COALESCE(u.salary_mode, FALSE) THEN u.fixed_salary_amount ELSE NULL END,
    CASE WHEN COALESCE(u.salary_mode, FALSE) THEN u.salary_start_date ELSE NULL END
FROM user_tenant_roles utr
JOIN app_user u ON u.user_id = utr.user_id
WHERE utr.role = 'BARBER'
  AND utr.branch_id IS NOT NULL
  AND (
      (COALESCE(u.salary_mode, FALSE) = TRUE AND u.fixed_salary_amount > 0 AND u.salary_frequency IS NOT NULL)
      OR
      (COALESCE(u.salary_mode, FALSE) = FALSE AND u.commission_percentage > 0)
  )
ON CONFLICT (tenant_id, branch_id, barber_user_id) DO NOTHING;
