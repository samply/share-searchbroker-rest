UPDATE samply.inquiry SET criteria = regexp_replace(criteria, 'http://schema.samply.de/[a-zA-z0-9]+/', 'http://schema.samply.de/common/', 'gi');