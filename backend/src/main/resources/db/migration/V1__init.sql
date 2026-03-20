create table if not exists supplier (
    id bigserial primary key,
    name varchar(128) not null,
    company varchar(128) not null
);

create table if not exists negotiation (
    id bigserial primary key,
    supplier_id bigint not null references supplier(id)
);

create table if not exists offer (
    id bigserial primary key,
    negotiation_id bigint not null references negotiation(id),
    round_number integer not null,
    price numeric(19,4) not null,
    payment_days integer not null,
    delivery_days integer not null,
    contract_months integer not null
);

create index if not exists idx_negotiation_supplier_id on negotiation (supplier_id);
create index if not exists idx_offer_negotiation_round on offer (negotiation_id, round_number);
