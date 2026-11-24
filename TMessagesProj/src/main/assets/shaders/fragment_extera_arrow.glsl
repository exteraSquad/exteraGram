precision highp float;

uniform sampler2D u_Texture;
uniform sampler2D u_NormalMap;
uniform sampler2D u_BackgroundTexture;
uniform sampler2D u_Engraving;
uniform float engraving;
uniform float f_xOffset;
uniform float f_alpha;
uniform mat4 world;

varying vec3 vNormal;
varying vec2 vUV;
varying vec3 modelViewVertex;

uniform float spec1;
uniform float spec2;
uniform float u_diffuse;
uniform float normalSpec;
uniform vec3 gradientColor1;
uniform vec3 gradientColor2;
uniform vec3 normalSpecColor;
uniform vec3 specColor;
uniform vec2 resolution;
uniform vec4 gradientPosition;

vec4 arrow(vec3 normal) {
    vec3 cameraPosition = vec3(0, 0, 100);
    vec3 vLightPosition2 = vec3(-400,400,400);
    vec3 vLightPosition4 = vec3(0,0,100);
    vec3 vLightPositionNormal = vec3(100,-200,400);

    vec3 vNormalW = normalize(vec3(world * vec4(normal, 0.0)));
    vec3 vTextureNormal = normalize(texture2D(u_NormalMap, (vUV + vec2(-f_xOffset, f_xOffset)) * 2.0).xyz * 2.0 - 1.0);

    vec3 finalNormal = normalize(vNormalW + vTextureNormal);

    vec3 color = texture2D(u_Texture, vUV).xyz;
    vec3 viewDirectionW = normalize(cameraPosition);

    vec3 angleW = normalize(viewDirectionW + vLightPosition2);
    float specComp2 = max(0., dot(vNormalW, angleW));
    specComp2 = pow(specComp2, max(1., 128.)) * spec1;

    angleW = normalize(viewDirectionW + vLightPosition4);
    float specComp3 = max(0., dot(vNormalW, angleW));
    specComp3 = pow(specComp3, max(1., 30.)) * spec2;

    float diffuse = max(dot(vNormalW, viewDirectionW), (1.0 - u_diffuse));

    float mixValue = distance(vUV,vec2(1,0));
    vec4 gradientColorFinal = vec4(mix(gradientColor1, gradientColor2, mixValue), 1.0);

    angleW = normalize(viewDirectionW + vLightPositionNormal);
    float normalSpecComp = max(0., dot(finalNormal, angleW));
    normalSpecComp = pow(normalSpecComp, max(1., 128.)) * normalSpec;

    angleW = normalize(viewDirectionW + vLightPosition2);
    float normalSpecComp2 = max(0., dot(finalNormal, angleW));
    normalSpecComp2 = pow(normalSpecComp2, max(1., 128.)) * normalSpec;

    vec4 normalSpecFinal = vec4(normalSpecColor, 0.0) * (normalSpecComp + normalSpecComp2);
    vec4 specFinal = vec4(color, 0.0) * (specComp2 + specComp3);

    vec4 fragColor = gradientColorFinal + specFinal + normalSpecFinal;
    vec4 backgroundColor = texture2D(u_BackgroundTexture, vec2(gradientPosition.x + (gl_FragCoord.x / resolution.x) * gradientPosition.y, gradientPosition.z + (1.0 - (gl_FragCoord.y / resolution.y)) * gradientPosition.w));
    return mix(backgroundColor, fragColor, diffuse) * f_alpha;
}

void main() {
    vec3 normal = vNormal;
    float mask = 0.0;
    if (engraving > 0.0 && vNormal.z < 0.0) {
        vec2 uv = 1.0 - vUV;
        float d = 1.5 / 1024.0;
        mask = texture2D(u_Engraving, uv).a;
        float dx = texture2D(u_Engraving, uv + vec2(d, 0.0)).a - texture2D(u_Engraving, uv - vec2(d, 0.0)).a;
        float dy = texture2D(u_Engraving, uv + vec2(0.0, d)).a - texture2D(u_Engraving, uv - vec2(0.0, d)).a;
        normal = normalize(normal + vec3(dx, -dy, 0.0) * (0.0099 / (2.0 * d)));
    }
    vec4 color = arrow(normal);
    gl_FragColor = vec4(color.rgb * (1.0 - 0.14 * mask), color.a);
}
