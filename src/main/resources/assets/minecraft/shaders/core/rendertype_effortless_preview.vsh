#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ChunkOffset;

out vec4 vertexColor;
out vec2 texCoord0;
out vec3 surfaceNormal;

void main() {
    vec3 pos = Position + ChunkOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    vertexColor = Color;
    texCoord0 = UV0;
    surfaceNormal = normalize(mat3(ModelViewMat) * Normal);
}
