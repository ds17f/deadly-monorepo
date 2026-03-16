output "instance_public_ip" {
  value = oci_core_instance.server.public_ip
}

output "instance_id" {
  value = oci_core_instance.server.id
}
