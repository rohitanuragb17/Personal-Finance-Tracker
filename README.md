# Student Result Management System

A beginner-friendly Java Servlet/JSP/JDBC project for managing student records and report cards. The Warm Academia interface is server-rendered and responsive.

## Features

- Admin manages accounts and roles; Admin and Teacher manage students and results; Student sees only their own results.
- Full create, read, update, delete flows for student details and marks.
- Searchable result archive, automatic grade/pass calculation, grouped printable report card.
- H2 file database through JDBC prepared statements; passwords hashed with PBKDF2.
- Server-side validation, container-managed sessions, CSRF tokens, output escaping, and role/ownership checks.
