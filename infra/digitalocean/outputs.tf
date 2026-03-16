output "droplet_ip" {
  value = digitalocean_droplet.server.ipv4_address
}

output "droplet_id" {
  value = digitalocean_droplet.server.id
}
