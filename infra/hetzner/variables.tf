variable "hcloud_token" {
  type        = string
  sensitive   = true
  description = "Hetzner Cloud API token (Read & Write)"
}

variable "ssh_public_key_path" {
  type    = string
  default = "../../ssh-key-2026-03-15.key.pub"
}

variable "location" {
  type        = string
  default     = "ash"
  description = "Hetzner location (ash = Ashburn VA, hil = Hillsboro OR, fsn1/nbg1/hel1 = EU)"
}

variable "server_type" {
  type        = string
  default     = "cpx11"
  description = "2 vCPU AMD / 2GB RAM / 40GB SSD — ~$4.35/mo, available in US locations"
}
