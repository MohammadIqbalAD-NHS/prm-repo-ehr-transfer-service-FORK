terraform {
      backend "s3" {
        bucket  = "prm-deductions-terraform-state"
        key     = "gp2gp-message-handler/terraform.tfstate"
        region  = "eu-west-2"
        encrypt = true
    }
}
