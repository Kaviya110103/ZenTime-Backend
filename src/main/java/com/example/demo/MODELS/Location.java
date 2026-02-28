package com.example.demo.MODELS;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "locations")
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private Double latitude;
    
    @NotBlank
    private Double longitude;

    @NotBlank
    private String name;

    @NotBlank
    private String address;

    /** Radius in metres */
    private Integer radius;

    @Column(name = "client_id")
    private Long clientId;
    
    

    /* ─────────── Getters & setters ─────────── */

    public Long getId()               { return id; }
    public void setId(Long id)        { this.id = id; }

    public Double getLatitude()       { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude()      { return longitude; }
    public void setLongitude(Double longitude){ this.longitude = longitude; }

    public String getName()           { return name; }
    public void setName(String name)  { this.name = name; }

    public String getAddress()        { return address; }
    public void setAddress(String address){ this.address = address; }

    public Integer getRadius()        { return radius; }
    public void setRadius(Integer radius) { this.radius = radius; }

    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }
}
