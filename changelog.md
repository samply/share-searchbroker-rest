# Change Log
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/)
and this project adheres to [Semantic Versioning](http://semver.org/).

## [8.4.0 - 2021-08-25]
### Added
- Change icinga project value by docker environment
- Monitoring api for active checks
### Changed
- Share-dto version 5.1.0
- Use gson while saving reply

## [8.3.0 - 2021-08-13]
### Added
- Save selected biobanks for the negotiator

## [8.2.4 - 2021-08-09]
### Fixed
- Api call to get the collection id by biobank name

## [8.2.3 - 2021-07-29]
### Changed
- share-common version 4.3.0

## [8.2.2 - 2021-07-29]
### Changed
- share-common version 4.2.0

## [8.2.1 - 2021-07-28]
### Fixed
- Icinga api call

## [8.2.0 - 2021-06-04]
### Changed
- Api call to get all sites and their directory id

## [8.1.1 - 2021-05-03]
### Fixed
- Add feature.properties to dockerfile

## [8.1.0 - 2021-03-04]
### Added
- Bridgehead can set their site via rest api
- Bridgehead can insert new site
- Feature toggle
### Changed
- Postgres plugin version 42.2.19
- Jooq plugin version 3.11.12
- Parent pom 11.1.1
### Fixed
- Bridgehead can delete their registration via rest api

## [8.0.0 - 2020-11-26]
- Github release
### Changed
- Samply parent 11.1.0
## Added
- Github Actions
- Google Code Style

## [7.2.0 - 2020-09-17]
### Added
- Add Statusreportitem job-config and inquiry-info for Icinga

## [7.1.0 - 2020-09-15]
### Changed
- Logic for inequality operator (only ICD-10)

## [7.0.0 - 2020-08-10]
### Added
- Add test inquiry for CQL 

## [6.1.2 - 2020-08-05]
### Fixed
- fixed chars in start.sh

## [6.1.1 - 2020-08-05]
### Delete
- deleted synchronized tags

## [6.1.0 - 2020-07-27]
### Added
- added samply.share.broker.conf to Dockerfile

## [6.0.3 - 2020-06-12]
### Changed
- Improved tests

## [6.0.2 - 2020-05-15]
### Changed
- CQL configuration (sample types)

## [6.0.1 - 2020-05-13]
### Changed
- Reverse order of reply (biobanks with most donors first)

## [6.0.0 - 2020-04-17]
### Changed
- Change interface for replies to frontend (Reply)
- Update share-dto

## [5.1.2 - 2020-05-04]
### Bugfix
- Creating empty CQL queries

## [5.1.1 - 2020-04-23]
### Changed
- no cache control for requests
- avoid using header parameters
### Fixed
- email sending

## [5.1.0 - 2020-03-31]
### Added
- Send statistics per e-mail

## [5.0.0 - 2020-03-30]
### Added
- Use Open API
- Added nTokens for identifying queries (SampleLocator and Negotiator)
### Changed
- Use JSON instead of XML for Query exchange (EssentialSimpleQueryDto) 
- Added CORS header

## [4.1.0 - 2020-02-17]
### Added
- Save quieries for statistical purposes

## [4.0.0 - 2020-02-12]
### Changed
- Use EssentialSimpleQueryDto instead of SimpleQueryDto
- JUnit4 --> JUnit5

## [3.4.7 - XXX]
### Changed
- Inquiries expire date from 28 days to 5 minutes
- User infos like name and email do not get stored in db

## [3.4.6 - 2019-11-08]
### Bugfix
- CQL queries (typos in permitted values)

## [3.4.5 - 2019-11-08]
### Bugfix
- CQL queries

## [3.4.4 - 2019-11-07]
### Changed
- Use proper CQL snippets for context related retrieves

## [3.4.3 - 2019-11-07]
### Bugfix
- Permitted value mapping MDR to CQL (for one CQL value only)

## [3.4.0 - 2019-11-04]
### Added
- Allow more than one CQL value per permitted MDR value

## [3.3.1 - 2019-10-30]
### Changed
- Update share-dto (CQL methods)

## [3.3.0 - 2019-09-27]
### Added
- Added CQL generation (including multiple codesystems and singleton statements)

## [3.2.1 - 2019-09-26]
### Bugfix
- Update share-dto (avoid using namespace)

## [3.2.0 - 2019-08-26]
### Changed
- Added default value 'QUERY' for query language
### Removed
- removed SampleContext and moved fields from Event to Donor

## [3.1.0 - 2019-07-02]
### Changed
- Cleaner code
- Removed unused model classes (Note and UserConsent)

## [3.0.0 - 2019-07-01]
### Changed
- Refactored Flyway scripts
- Moved SimpleQueryDto2ShareXmlTransformer from UI to REST
- Split table Inquiry into Inquiry and InquiryCriteria

## [2.2.0 - 2019-06-06]
### Changed
- Update version of samply.common.config 3.0.3 -> 3.1.0 
- Update version of samply.share.common 3.1.3 -> 3.2.0 
- Update version of samply.common.mailing 2.1.3 -> 2.2.0 

## [2.1.0 - 2018-05-07]
### Changed
- Update share.common: 3.0.0 -> 3.1.0 (including new samply.mdrfaces without jquery)
- Update other samply dependencies
- Partially Update Jersey 1.x -> 2.26 (due to problems with multipart, asm and Java 8)
- Use webjar for fileinput.js
- Reupdate JQuery 1.11.1 -> 3.3.1-1
- Webjar bootstrap-datetimepicker: 4.17.43 -> 4.17.47
- Webjar select2: 4.0.3 -> 4.0.5
- Update other general and webjar dependencies

## [2.0.0 - 2018-03-19]
### Added
- Parent POM 10.1 (Java 8 )
- Introduce profiles for setting type of project (osse, dktk, gbn)
- Update some library versions (e.g. JQuery 1.11.1 => 3.3.1-1)

## [1.3.2 - 201y-mm-dd]
### Added
- Accept "samply-xml-namespace" header with desired namespace, e.g. "common"
### Changed
- Use xml namespace "common" instead of "osse" or "ccp" for queries
### Deprecated
### Removed
- Deleted unused table user_bank from database
- Observer endpoint
### Fixed
### Security


## [1.3.1 - 2017-12-06]
### Changed
- Upgrade samply.share.common to 1.2.2-SNAPSHOT

### Removed
- Don't convert date values when receiving queries from central mds database

### Fixed
- Erroneous access right warnings removed
- Reword some misleading labels

## [1.3.0 - 2017-11-08]
### Added
- Flyway for DB migrations
- Jooq Codegeneration via maven plugin
- Allow to specify desired viewfields for an inquiry

### Changed
- Switch Java language level to 1.8 (was 1.7)
- Adapt to updated samply auth version (roles changed)

## [1.2.6 - 2017-08-24]
### Added
- Report version information to icinga
- Allow to send telemetric data to icinga
- Provide a reference query for monitoring purposes
- README.md
- CHANGELOG.md

### Changed
- When a user logs in with a different samply auth id, but the same email address, change the user record to the new auth id
- Use library for Bootstrap Style Messages
