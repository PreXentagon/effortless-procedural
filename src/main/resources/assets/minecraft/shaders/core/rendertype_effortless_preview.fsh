#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec4 TintColor;

in vec4 vertexColor;
in vec2 texCoord0;
in vec3 surfaceNormal;

out vec4 fragColor;

void main() {
    vec4 textureColor = texture(Sampler0, texCoord0);
    if (textureColor.a < 0.1) {
        discard;
    }
    vec3 lightDirection = normalize(vec3(-0.35, 0.70, 0.55));
    float diffuse = max(dot(normalize(surfaceNormal), lightDirection), 0.0);
    float studioLight = 0.72 + diffuse * 0.28;
    vec4 color = textureColor * vertexColor * ColorModulator * TintColor;
    fragColor = vec4(color.rgb * studioLight, color.a);
}
