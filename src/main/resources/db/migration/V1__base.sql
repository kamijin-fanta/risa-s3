create table access_key (
  key uuid not null primary key,
  secret string not null,
  bucket string not null
);

create table bucket (
  id string not null primary key,
  owner string not null,
  unique (id)
);

create table object (
  bucket string not null,
  path string not null,
  version serial not null,
  file uuid not null,
  status int not null,
  hash string not null,
  size int not null,
  unique (bucket, path, version)
);

create table file (
  bucket string not null,
  id uuid not null,
  number int not null,
  volume_group string not null,
  chunk_name string not null,
  offset_byte int not null,
  size_byte int not null,
  unique (bucket, id)
);

create table volume_group (
  id string not null primary key,
  status int not null  -- 0: ok, 1: read-only, 2: dead
);

create table volume_node (
  volume_group string not null,
  id string not null,
  address string not null, -- 192.168.10.12:9551
  capacity int not null,
  free int not null,
  unique (volume_group, id)
);
