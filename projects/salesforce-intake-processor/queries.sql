CREATE STREAM s_dist_sf_campaign_1 
    WITH (KAFKA_TOPIC='distribution-salesforce-evt-campaign.1', partitions=1, value_format = 'avro');

CREATE STREAM s_key_dist_sf_campaign_1 
    WITH (KAFKA_TOPIC='_key_distribution-salesforce-evt-campaign.1', partitions=1, value_format='avro') AS
    SELECT *
    FROM s_dist_sf_campaign_1
    PARTITION BY id;


CREATE STREAM s_dist_sf_user_1 
    WITH (KAFKA_TOPIC='distribution-salesforce-evt-user.1', partitions=1, value_format = 'avro');

CREATE STREAM s_key_dist_sf_user_1
    WITH(KAFKA_TOPIC='ksql-l1.distribution-cdc-userLookup.1', partitions=1, value_format='avro') AS
    SELECT id, worker_id, durable_id
    FROM s_dist_sf_user_1
    PARTITION BY id;

CREATE TABLE t_l1_dist_userlookup_1 
    WITH(KAFKA_TOPIC='ksql-l1.distribution-cdc-userLookup.1', key='id', partitions=1, value_format='avro');


CREATE STREAM s_join_createdby_dist_campaign_1 WITH(partitions=1, value_format='avro') AS
    SELECT c.id AS id,
           c.operation,
           c.record_type,
           c.owner_id,
           c.durable_id AS durable_id,
           c.created_by_id,
           ul.worker_id AS worker_created_by_id
    FROM s_key_dist_sf_campaign_1 c
    JOIN t_l1_dist_userlookup_1 ul ON c.created_by_id = ul.id;

CREATE STREAM s_join_owner_dist_campaign_1 WITH(partitions=1, value_format='avro') AS
    SELECT c.id AS id,
           c.operation,
           c.record_type,
           c.owner_id,
           c.durable_id AS durable_id,
           c.created_by_id,
           c.worker_created_by_id,
           ul.worker_id AS worker_owner_id
    FROM s_join_createdby_dist_campaign_1 c
    JOIN t_l1_dist_userlookup_1 ul ON c.owner_id = ul.id;

CREATE STREAM s_key_joined_dist_sf_campaign_1 
    WITH (KAFKA_TOPIC='ksql-l1.distribution-cdc-campaign.1', partitions=1, value_format='avro') AS
    SELECT *
    FROM s_join_owner_dist_campaign_1
    PARTITION BY id;

CREATE TABLE t_l1_dist_campaign WITH(KAFKA_TOPIC='ksql-l1.distribution-cdc-campaign.1', key='id', partitions=1, value_format='avro');