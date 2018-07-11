create table access_key (
  id char(36) not null primary key, -- uuid
  secret char(64) not null,  -- sha256(rand)
  bucket char(64) not null
);

create table bucket (
  id varchar(64) not null primary key,
  owner varchar(64) not null
);

create table object (
  bucket varchar(64) not null,
  path varchar(1024) not null,
  version datetime not null,
  file char(36) not null,  -- uuid
  status int not null,  -- 0: ok, 1: uploading, 2: deleted
  hash char(64) not null,  -- sha256
  size int not null,
  primary key(bucket, path, version)
);

create table file (
  bucket varchar(64) not null,
  id char(36) not null, -- uuid
  number int not null,
  volume_group int not null,
  chunk_name varchar(128) not null,
  offset_byte int not null,
  size_byte int not null,
  primary key(bucket, id)
);

create table volume_group (
  id int not null primary key auto_increment,
  status int not null  -- 0: ok, 1: read-only, 2: dead
);

create table volume_node (
  id int not null auto_increment,
  volume_group int not null,
  address varchar(128) not null, -- 192.168.10.12:9551
  capacity bigint not null,
  free bigint not null,
  primary key(id, volume_group),
  index(volume_group, id)
);

create table volume_file (
  volume_group int not null,
  tablet varchar(128),
  name varchar(128),
  primary key(volume_group, tablet, name)
);
