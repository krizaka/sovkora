package com.krizaka.sovkora.tools.infrastructure.adapter.persistence.entity;

import jakarta.persistence.*;

/**
 * JPA Entity mapping {@code platform_tool_permissions}: one row per (tool, role) an administrator
 * granted (ADR-091). A tool with no row uses the roles it declared when it registered.
 */
@Entity
@Table(name = "platform_tool_permissions")
public class ToolPermissionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @Column(name = "tool_id", nullable = false, length = 255)
  private String toolId;

  @Column(name = "required_role", nullable = false, length = 100)
  private String requiredRole;

  /** Default constructor required by JPA/Hibernate. */
  public ToolPermissionEntity() {
    /* JPA requires no-arg constructor */
  }

  public Integer getId() {
    return id;
  }

  public String getToolId() {
    return toolId;
  }

  public void setToolId(String toolId) {
    this.toolId = toolId;
  }

  public String getRequiredRole() {
    return requiredRole;
  }

  public void setRequiredRole(String requiredRole) {
    this.requiredRole = requiredRole;
  }
}
