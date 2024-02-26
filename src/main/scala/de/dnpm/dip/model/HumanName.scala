package de.dnpm.dip.model



final case class HumanName
(
  givenName: String,
  middleName: Option[String],
  familyName: String,
  title: Option[String],
  prefix: Option[String],
  postfix: Option[String],
)

