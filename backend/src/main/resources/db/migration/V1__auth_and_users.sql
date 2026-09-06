-- Step 1: identity, org scoping, sessions, host applications, audit trail.
-- Phone is the primary identifier (phone-first OTP login); email + password is
-- the secondary path, so password_hash and email are both nullable.

create table users (
    id                uuid         primary key,
    phone             varchar(20)  not null,
    email             varchar(255),
    password_hash     varchar(100),
    full_name         varchar(150),
    status            varchar(32)  not null,
    kyc_status        varchar(32)  not null default 'NONE',
    locale            varchar(8)   not null default 'mn',
    phone_verified_at timestamptz,
    email_verified_at timestamptz,
    last_login_at     timestamptz,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    version           bigint       not null default 0,
    constraint uq_users_phone unique (phone),
    constraint ck_users_phone_e164 check (phone ~ '^\+[1-9][0-9]{6,14}$'),
    constraint ck_users_status check (status in ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED')),
    constraint ck_users_kyc_status check (kyc_status in ('NONE', 'PENDING', 'VERIFIED', 'REJECTED'))
);

-- Email is optional but unique when present. Stored already lower-cased by the
-- application, so a plain unique index is enough (no citext extension needed).
create unique index uq_users_email on users (email) where email is not null;

comment on column users.password_hash is 'BCrypt. Null for OTP-only accounts that never set a password.';

-- Business accounts. Hotels are owned by an organization (spec section 5:
-- Hotel.ownerOrgId), and HOTEL_MANAGER / HOTEL_STAFF grants are scoped to one.
create table organizations (
    id              uuid        primary key,
    name            varchar(200) not null,
    type            varchar(32)  not null,
    registration_no varchar(64),
    owner_user_id   uuid         not null references users (id),
    status          varchar(32)  not null default 'PENDING',
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    version         bigint       not null default 0,
    constraint ck_organizations_type check (type in ('HOTEL_BUSINESS', 'INDIVIDUAL_HOST')),
    constraint ck_organizations_status check (status in ('PENDING', 'ACTIVE', 'SUSPENDED', 'REJECTED'))
);

create unique index uq_organizations_registration_no
    on organizations (registration_no) where registration_no is not null;
create index ix_organizations_owner on organizations (owner_user_id);

-- One row per role grant, so an account can hold CLIENT plus one host role.
create table user_roles (
    id         uuid        primary key,
    user_id    uuid        not null references users (id) on delete cascade,
    role       varchar(32) not null,
    org_id     uuid        references organizations (id) on delete cascade,
    granted_at timestamptz not null default now(),
    granted_by uuid        references users (id),
    constraint ck_user_roles_role check (role in ('CLIENT', 'HOUSE_OWNER', 'HOTEL_MANAGER', 'HOTEL_STAFF', 'SUPER_ADMIN')),
    -- Hotel-side roles are meaningless without an organization; the others must not carry one.
    constraint ck_user_roles_org_scope check (
        (role in ('HOTEL_MANAGER', 'HOTEL_STAFF') and org_id is not null)
        or (role in ('CLIENT', 'HOUSE_OWNER', 'SUPER_ADMIN') and org_id is null)
    )
);

create unique index uq_user_roles_grant
    on user_roles (user_id, role, coalesce(org_id, '00000000-0000-0000-0000-000000000000'::uuid));
create index ix_user_roles_user on user_roles (user_id);
create index ix_user_roles_org on user_roles (org_id) where org_id is not null;

-- Rotating refresh tokens. Only the SHA-256 of the token is stored, so a
-- database leak does not hand over live sessions.
create table refresh_tokens (
    id           uuid        primary key,
    user_id      uuid        not null references users (id) on delete cascade,
    token_hash   varchar(64) not null,
    device_label varchar(120),
    ip           varchar(64),
    issued_at    timestamptz not null default now(),
    expires_at   timestamptz not null,
    revoked_at   timestamptz,
    replaced_by  uuid        references refresh_tokens (id),
    constraint uq_refresh_tokens_hash unique (token_hash)
);

create index ix_refresh_tokens_active on refresh_tokens (user_id) where revoked_at is null;
create index ix_refresh_tokens_expiry on refresh_tokens (expires_at);

-- Self-service request to become a host. The Step 4 admin console reviews these.
create table host_applications (
    id                  uuid        primary key,
    user_id             uuid        not null references users (id) on delete cascade,
    requested_role      varchar(32) not null,
    org_name            varchar(200),
    org_registration_no varchar(64),
    note                text,
    status              varchar(32) not null default 'PENDING',
    decided_by          uuid        references users (id),
    decided_at          timestamptz,
    decision_note       text,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    version             bigint      not null default 0,
    constraint ck_host_applications_role check (requested_role in ('HOUSE_OWNER', 'HOTEL_MANAGER')),
    constraint ck_host_applications_status check (status in ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN'))
);

-- At most one open application per user per role.
create unique index uq_host_applications_pending
    on host_applications (user_id, requested_role) where status = 'PENDING';
create index ix_host_applications_status on host_applications (status, created_at desc);

create table audit_logs (
    id          uuid        primary key,
    actor_id    uuid        references users (id),
    action      varchar(64) not null,
    target_type varchar(64),
    target_id   varchar(64),
    metadata    jsonb       not null default '{}'::jsonb,
    ip          varchar(64),
    created_at  timestamptz not null default now()
);

create index ix_audit_logs_created on audit_logs (created_at desc);
create index ix_audit_logs_actor on audit_logs (actor_id, created_at desc);
create index ix_audit_logs_target on audit_logs (target_type, target_id);
