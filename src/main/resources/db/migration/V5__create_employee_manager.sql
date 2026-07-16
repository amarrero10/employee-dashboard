CREATE TABLE employee_manager (
                                  employee_id BIGSERIAL NOT NULL,
                                  manager_id BIGSERIAL NOT NULL,

                                  PRIMARY KEY (employee_id, manager_id),

                                  CONSTRAINT fk_employee
                                      FOREIGN KEY (employee_id)
                                          REFERENCES employees(id)
                                          ON DELETE CASCADE,

                                  CONSTRAINT fk_manager
                                      FOREIGN KEY (manager_id)
                                          REFERENCES managers(id)
                                          ON DELETE CASCADE
);