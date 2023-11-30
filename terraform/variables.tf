variable "region" {
  type    = string
  default = "eu-west-2"
}

variable "repo_name" {
  type    = string
  default = "prm-repo-ehr-transfer-service"
}

variable "environment" {}

variable "component_name" {
  type    = string
  default = "ehr-transfer-service"
}
variable "task_image_tag" {}
variable "task_cpu" {}
variable "task_memory" {}

variable "service_desired_count" {}

variable "log_level" {
  type    = string
  default = "debug"
}

variable "period_of_age_of_message_metric" {
  default = "1800"
}

variable "threshold_approx_age_oldest_message" {
  default = "300"
}

variable "timeout_in_seconds" {
  description = "Timeout in seconds at which EHR in transfer is deemed complete and failed if not entire EHR received"
  default = "14400"
}

variable "timeout_scheduler_fixed_rate_in_seconds" {
  default = "3600"
}

variable "processing_period_milliseconds" {
  type = number
  default = 10000
}

variable "ehr_response_poll_period_milliseconds" {
  type = number
  default = 10000
}

variable "ehr_response_poll_limit" {
  type = number
  default = 60
}