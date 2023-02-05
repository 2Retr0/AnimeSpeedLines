#version 330

#define PI 3.141593

uniform sampler2D DiffuseSampler; // Main texture
uniform float     STime;          // Time in seconds + tick delta
uniform float     Weight;         // Intensity
uniform float     BiasAngle;         // Intensity
uniform float     BiasWeight;

in vec2 texCoord;
out vec4 fragColor;

// signed distance to a n-star polygon with external angle en
// SDF Function adapted from Iñigo Quiles https://iquilezles.org/articles/distfunctions2d/
// p : position, r : radius, n : num sides, m : angle divisor
float lines_sdf(in vec2 p, in float r, in int n, in float m) // m=[2,n]
{
    // these 4 lines can be precomputed for a given shape
    float an = PI / float(n);
    float en = PI / m;
    vec2 acs = vec2(cos(an), sin(an));
    vec2 ecs = vec2(cos(en), sin(en)); // ecs=vec2(0,1) and simplify, for regular polygon,

    // reduce to first sector
    float bn = mod(atan(p.x, p.y), 2. * an) - an;
    p = length(p) * vec2(cos(bn), abs(sin(bn)));

    // line sdf
    p -= r * acs;
    p += ecs * clamp(-dot(p, ecs), 0., r * acs.y / ecs.y);
    return length(p) * sign(p.x);
}



float rand(in float n) {
    return fract(sin(n * 1234.5 + 5432.1) * 5432.1);
}



mat2 rotate(in float a) {
    return mat2(cos(a), -sin(a), sin(a), cos(a));
}



void main() {

    vec3 tex = texture2D(DiffuseSampler, texCoord).rgb;
    vec2 st = texCoord - 0.5;

    // These should be calculated on the CPU
    float strength = Weight; // mod(iTime * 0.1, 0.8) + 0.2;
    float str_norm = (1. - 0.8) * strength + 0.8;
    // float bias_angle = floor(STime) * 0.25 * (2. * PI);
    float bias = BiasWeight;
    st += -0.08 * bias * vec2(cos(BiasAngle), sin(BiasAngle));

    // Normalize angle to be between 0 and 1.
    float angle = atan(st.t, st.s) + PI;
    float angle_norm = (0.5 * angle) / PI;


    // These should be calculated on the CPU
    float angle_bias = bias * cos(angle - BiasAngle) + (1. - bias);


    // We generate a seed for each line based on its angle.
    float s_rand = 0.5 * rand(floor(angle_norm * 90.)) + 0.5;

    // We also denote some parameters for the animation progress
    float s_time = s_rand * STime * 5.;
    float p_shape = mod(s_time, 1.); // Animation progress
    float p_iter  = floor(s_time);   // Animation Iteration

    //
    float s_radius = 1. - pow(max(0., 1.6 * p_shape - 0.6), 2.);

    st *= 0.6; // Scale out space so we can make 'sharper' lines.
    st *= rotate(s_rand + rand(p_iter)); // Rotate by a random amount


    // Alpha will be TARGET_ALPHA when line begins to recede
    float s_alpha =  1. - 2. * p_shape * 0.2 * strength;


    float w = 162. * s_radius;
    w *= 0.4 * s_rand;          // Add random factor to spikyness.
    w *= angle_bias * str_norm; //
    w = max(2.0, w);

    // sdf
    float d = lines_sdf(st, 0.7, 90, w);


    // colorize
    vec3 col = (d > 0.) ? vec3(1.) : tex;
    col = mix(col, vec3(1.), 1. - smoothstep(0., 0.001, abs(d))); // smooth line edges
    col = mix(col, tex, s_alpha);             // 'transparency' of line

    // Draw debug lines (red is path of line).
    // col = fract(0.5 * (atan(st.t, st.s) / PI + 1.) * n) > 0.95 ? vec3(1., 0.25, 0.3) : col;

    fragColor = vec4(col, 1.);
}
