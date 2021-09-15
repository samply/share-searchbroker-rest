SET search_path TO samply;

ALTER TABLE "reply" ADD COLUMN retrievedAt TIMESTAMP;
ALTER TABLE "ntoken_query" DROP COLUMN selected_biobank;
