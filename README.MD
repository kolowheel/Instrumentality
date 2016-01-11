## Vulnerabilities: 
    No check if current user permit to manage jobs
## Storage :
    For storing user data MongoDB is used with "to parent link" approach
    For storing files GridFS is used, because of nice api and non-blocking approach
## Front-end:
    Front-end is number of plain html-based bootstrap'ed pages
## Back-end:
    Main back-end framework is Play-framework 2.4.
    MongoDB driver is reactivemongo.
    OAuth2 access provider Silhouette is used.
## Deploy:
    For building dist package run
  ```scala
  sbt dist
  ```
  
