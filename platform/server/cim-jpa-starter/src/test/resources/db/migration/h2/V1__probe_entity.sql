-- T1.5：H2 迁移样例（与 com.cim.jpa.flywayit.ProbeEntity 映射一致，用于验证 ddl-auto=validate）
create table probe_entity (
    id   varchar(64) not null,
    name varchar(128),
    primary key (id)
);
