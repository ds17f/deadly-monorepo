resource "digitalocean_ssh_key" "deadly" {
  name       = "deadly-dev-key"
  public_key = file(var.ssh_public_key_path)
}

resource "digitalocean_droplet" "server" {
  name     = var.droplet_name
  region   = var.region
  size     = var.droplet_size
  image    = "ubuntu-22-04-x64"
  ssh_keys = [digitalocean_ssh_key.deadly.fingerprint]

  tags = ["deadly", "dev"]
}
