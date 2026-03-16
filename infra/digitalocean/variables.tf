variable "do_token" {
  type        = string
  sensitive   = true
  description = "DigitalOcean API token"
}

variable "ssh_public_key_path" {
  type    = string
  default = "../../ssh-key-2026-03-15.key.pub"
}

variable "region" {
  type    = string
  default = "nyc1"
}

variable "droplet_name" {
  type    = string
  default = "deadly-dev"
}

variable "droplet_size" {
  type        = string
  default     = "s-2vcpu-4gb"
  description = "4GB RAM / 2 vCPU — $24/mo, covered by free credits"
}
