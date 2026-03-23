resource "digitalocean_ssh_key" "deadly" {
  name       = "deadly-dev-key"
  public_key = file(var.ssh_public_key_path)
}

# Original dev server — do not modify
resource "digitalocean_droplet" "server" {
  name      = var.droplet_name
  region    = var.region
  size      = var.droplet_size
  image     = "ubuntu-22-04-x64"
  ssh_keys  = [digitalocean_ssh_key.deadly.fingerprint]
  user_data = file("../cloud-init.sh")

  tags = ["deadly", "dev"]
}

# Per-environment droplets managed by infra-manage.yml
# Builds happen in GHA, so these only need enough RAM to run containers (~1GB)

resource "digitalocean_droplet" "alpha" {
  name      = "deadly-alpha"
  region    = var.region
  size      = "s-1vcpu-1gb"
  image     = "ubuntu-22-04-x64"
  ssh_keys  = [digitalocean_ssh_key.deadly.fingerprint]
  user_data = file("../cloud-init.sh")

  tags = ["deadly", "alpha"]
}

resource "digitalocean_droplet" "beta" {
  name      = "deadly-beta"
  region    = var.region
  size      = "s-1vcpu-1gb"
  image     = "ubuntu-22-04-x64"
  ssh_keys  = [digitalocean_ssh_key.deadly.fingerprint]
  user_data = file("../cloud-init.sh")

  tags = ["deadly", "beta"]
}

resource "digitalocean_droplet" "prod" {
  name      = "deadly-prod"
  region    = var.region
  size      = "s-1vcpu-2gb"
  image     = "ubuntu-22-04-x64"
  ssh_keys  = [digitalocean_ssh_key.deadly.fingerprint]
  user_data = file("../cloud-init.sh")

  tags = ["deadly", "prod"]
}
