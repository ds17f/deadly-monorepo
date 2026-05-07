resource "hcloud_ssh_key" "deadly" {
  name       = "deadly-dev-key"
  public_key = file(var.ssh_public_key_path)
}

# Builds happen in GHA, so these only need enough RAM to run containers (~1-2GB).

resource "hcloud_server" "beta" {
  name        = "deadly-beta"
  image       = "ubuntu-22.04"
  server_type = var.server_type
  location    = var.location
  ssh_keys    = [hcloud_ssh_key.deadly.id]
  user_data   = file("../cloud-init.sh")

  public_net {
    ipv4_enabled = true
    ipv6_enabled = true
  }

  labels = {
    project = "deadly"
    env     = "beta"
  }
}

resource "hcloud_server" "prod" {
  name        = "deadly-prod"
  image       = "ubuntu-22.04"
  server_type = var.server_type
  location    = var.location
  ssh_keys    = [hcloud_ssh_key.deadly.id]
  user_data   = file("../cloud-init.sh")

  public_net {
    ipv4_enabled = true
    ipv6_enabled = true
  }

  labels = {
    project = "deadly"
    env     = "prod"
  }
}
