CREATE TABLE managers (
                           id BIGSERIAL PRIMARY KEY,

                           first_name VARCHAR(255) NOT NULL,

                           last_name VARCHAR(255) NOT NULL,

                           email VARCHAR(255) UNIQUE NOT NULL,

                           phoneNumber VARCHAR(20),

                           role VARCHAR(20) UNIQUE NOT NULL,

                           created_at TIMESTAMP NOT NULL,

                           updated_at TIMESTAMP NOT NULL
);