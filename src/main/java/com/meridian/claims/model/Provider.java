package com.meridian.claims.model;

import java.util.Date;

public class Provider {

    private int id;
    private String npi;
    private String name;
    private ProviderType providerType;
    private String specialty;
    private NetworkStatus networkStatus;
    private String phone;
    private String address;
    private Date deletedAt;
    private Date createdAt;
    private Date updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getNpi() { return npi; }
    public void setNpi(String npi) { this.npi = npi; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ProviderType getProviderType() { return providerType; }
    public void setProviderType(ProviderType providerType) { this.providerType = providerType; }

    public String getSpecialty() { return specialty; }
    public void setSpecialty(String specialty) { this.specialty = specialty; }

    public NetworkStatus getNetworkStatus() { return networkStatus; }
    public void setNetworkStatus(NetworkStatus networkStatus) { this.networkStatus = networkStatus; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Date getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Date deletedAt) { this.deletedAt = deletedAt; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
