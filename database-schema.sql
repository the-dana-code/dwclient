create table items
(
    item_name         TEXT    default ''    not null
        primary key,
    description       TEXT    default ''    not null,
    appraise_text     TEXT    default ''    not null,
    weight            TEXT    default ''    not null,
    dollar_value      REAL    default '0.0' not null,
    searchable        INTEGER default '0'   not null,
    special_find_note TEXT    default ''    not null
);

create table npc_info
(
    npc_id   TEXT    default ''  not null
        primary key,
    map_id   INTEGER default '0' not null,
    npc_name TEXT    default ''  not null,
    room_id  TEXT    default ''  not null
);

create table npc_items
(
    npc_id       TEXT default '' not null,
    item_name    TEXT default '' not null,
    sale_price   TEXT default '' not null,
    special_note TEXT default '' not null,
    primary key (npc_id, item_name)
);

create table room_descriptions
(
    room_hash TEXT default '' not null
        primary key,
    room_id   TEXT default '' not null
);

create table room_exits
(
    room_id    TEXT not null,
    connect_id TEXT not null,
    exit       TEXT not null,
    guessed    INT  not null,
    primary key (room_id, connect_id)
);

create table rooms
(
    room_id    TEXT    not null
        primary key,
    map_id     INTEGER not null,
    xpos       INTEGER not null,
    ypos       INTEGER not null,
    room_short TEXT    not null,
    room_type  TEXT    not null
);

create index by_map
    on rooms (map_id);

create table shop_items
(
    room_id    TEXT default '' not null,
    item_name  TEXT default '' not null,
    sale_price TEXT default '' not null,
    primary key (room_id, item_name)
);

create table user_data
(
    datid  INTEGER default '0' not null
        primary key,
    rawdat TEXT    default ''  not null
);


