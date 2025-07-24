attribute vec4 a_Position;
attribute vec3 a_Normal;

uniform mat4 u_MVPMatrix;
uniform mat4 u_ModelMatrix;
uniform vec3 u_LightDirection;

varying float v_Y;
varying float v_LightIntensity;

void main() {
    gl_Position = u_MVPMatrix * a_Position;
    v_Y = a_Position.y;

    vec3 worldNormal = normalize(mat3(u_ModelMatrix) * a_Normal);
    v_LightIntensity = max(dot(worldNormal, u_LightDirection), 0.2);
} 