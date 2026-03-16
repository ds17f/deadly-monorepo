terraform {
  required_providers {
    digitalocean = {
      source  = "digitalocean/digitalocean"
      version = "~> 2.0"
    }
  }
  required_version = ">= 1.0"

  backend "s3" {
    bucket                      = "deadly-tfstate"
    key                         = "digitalocean/terraform.tfstate"
    endpoints                   = { s3 = "https://s3.us-west-004.backblazeb2.com" }
    region                      = "us-west-004"
    skip_credentials_validation = true
    skip_requesting_account_id  = true
    skip_metadata_api_check     = true
    skip_region_validation      = true
    skip_s3_checksum            = true
  }
}

provider "digitalocean" {
  token = var.do_token
}
