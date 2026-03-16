variable "tenancy_ocid" {
  type    = string
  default = "ocid1.tenancy.oc1..aaaaaaaa4a4snnwzwemjpohdntkdupogmx2dai2ohyk7drjt3w5d6sy4ijjq"
}

variable "user_ocid" {
  type    = string
  default = "ocid1.user.oc1..aaaaaaaadg2wgktmg4klh745pydvj3lxiuscug3pevdou4c4o73khyl4vyxq"
}

variable "compartment_ocid" {
  type    = string
  default = "ocid1.tenancy.oc1..aaaaaaaa4a4snnwzwemjpohdntkdupogmx2dai2ohyk7drjt3w5d6sy4ijjq"
}

variable "region" {
  type    = string
  default = "us-phoenix-1"
}

variable "fingerprint" {
  type    = string
  default = "2c:03:b7:7a:b4:77:38:19:b9:aa:ee:1c:e9:32:02:94"
}

variable "private_key_path" {
  type    = string
  default = "~/.oci/oci_api_key.pem"
}

variable "ssh_public_key_path" {
  type    = string
  default = "../../ssh-key-2026-03-15.key.pub"
}

variable "instance_display_name" {
  type    = string
  default = "deadly-server"
}

variable "availability_domain_index" {
  type    = number
  default = 0
}
