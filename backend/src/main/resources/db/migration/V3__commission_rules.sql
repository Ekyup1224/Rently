-- Platform take rate. Effective-dated rather than mutable, so changing the rate
-- never rewrites the economics of bookings already made: every booking records
-- which rule it was priced under.

create table commission_rules (
    id                 uuid          primary key,
    scope              varchar(24)   not null,
    category           varchar(32),
    host_fee_percent   numeric(5, 2) not null,
    guest_fee_percent  numeric(5, 2) not null default 0,
    effective_from     timestamptz   not null default now(),
    effective_to       timestamptz,
    note               varchar(255),
    created_by         uuid          references users (id),
    created_at         timestamptz   not null default now(),

    constraint ck_commission_scope check (scope in ('GLOBAL', 'PROPERTY_TYPE', 'HOTEL')),
    constraint ck_commission_host_percent check (host_fee_percent between 0 and 100),
    constraint ck_commission_guest_percent check (guest_fee_percent between 0 and 100),
    constraint ck_commission_window check (effective_to is null or effective_to > effective_from),
    -- A GLOBAL rule applies to everything and takes no category; a scoped rule needs one.
    constraint ck_commission_category check (
        (scope = 'GLOBAL' and category is null) or (scope <> 'GLOBAL' and category is not null))
);

create index ix_commission_rules_lookup on commission_rules (scope, category, effective_from desc);

-- Starting rate: 10% host-side, no guest service fee. Changeable from the admin
-- console; this row exists so pricing works on a fresh database.
insert into commission_rules (id, scope, category, host_fee_percent, guest_fee_percent, note)
values ('01900000-0000-7000-8000-000000000001', 'GLOBAL', null, 10.00, 0.00,
        'Launch rate: 10% host-side, no guest service fee');
