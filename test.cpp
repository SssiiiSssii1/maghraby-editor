/**
 * ============================================================================
ption: Self-contained 3D ASCII Ray Tracer & Linear Algebra Engine
 * Standard: C++17 / C++20 compatible
 * Features Tested:
 *   - Preprocessor directives (#include, #define, #ifdef)
 *   - Object-Oriented design (polymorphism, virtual destructors, overrides)
 *   - Operator overloading and Vector math
 *   - Multi-line block comments and single-line docstrings
 *   - String literals, escape sequences, floating-point numbers
 *   - Standard library usage (chrono, cmath, vector, iomanip, memory)
 * ============================================================================
 */

#include <iostream>
#include <vector>
#include <string>
#include <cmath>
#include <memory>
#include <chrono>
#include <iomanip>
#include <algorithm>
#include <sstream>

#define PI 3.14159265358979323846
#define EPSILON 1e-4f
#define DEFAULT_WIDTH 80
#define DEFAULT_HEIGHT 35

// ---------------------------------------------------------------------------
// 3D Vector Math Library
// ---------------------------------------------------------------------------
struct Vec3 {
    float x, y, z;

    constexpr Vec3() : x(0.0f), y(0.0f), z(0.0f) {}
    constexpr Vec3(float x, float y, float z) : x(x), y(y), z(z) {}

    // Vector arithmetic operator overloads
    Vec3 operator+(const Vec3& o) const { return Vec3(x + o.x, y + o.y, z + o.z); }
    Vec3 operator-(const Vec3& o) const { return Vec3(x - o.x, y - o.y, z - o.z); }
    Vec3 operator*(float s) const       { return Vec3(x * s, y * s, z * s); }
    Vec3 operator/(float s) const       { return Vec3(x / s, y / s, z / s); }
    Vec3 operator-() const              { return Vec3(-x, -y, -z); }

    Vec3& operator+=(const Vec3& o) {
        x += o.x; y += o.y; z += o.z;
        return *this;
    }

    float dot(const Vec3& o) const {
        return x * o.x + y * o.y + z * o.z;
    }

    Vec3 cross(const Vec3& o) const {
        return Vec3(
            y * o.z - z * o.y,
            z * o.x - x * o.z,
            x * o.y - y * o.x
        );
    }

    float lengthSquared() const {
        return x * x + y * y + z * z;
    }

    float length() const {
        return std::sqrt(lengthSquared());
    }

    Vec3 normalized() const {
        float len = length();
        return (len > 0.0f) ? (*this / len) : Vec3();
    }
};

inline Vec3 operator*(float s, const Vec3& v) {
    return v * s;
}

// ---------------------------------------------------------------------------
// Ray Optics
// ---------------------------------------------------------------------------
struct Ray {
    Vec3 origin;
    Vec3 direction;

    Ray(const Vec3& origin, const Vec3& direction)
        : origin(origin), direction(direction.normalized()) {}

    Vec3 pointAt(float t) const {
        return origin + direction * t;
    }
};

struct HitRecord {
    float t;
    Vec3 point;
    Vec3 normal;
    char materialGlyph;
};

// ---------------------------------------------------------------------------
// Scene Geometry (Polymorphic Entities)
// ---------------------------------------------------------------------------
class SceneObject {
public:
    virtual ~SceneObject() = default;
    virtual bool hit(const Ray& r, float tMin, float tMax, HitRecord& rec) const = 0;
};

class Sphere : public SceneObject {
private:
    Vec3 center;
    float radius;
    char glyph;

public:
    Sphere(const Vec3& center, float radius, char glyph = '@')
        : center(center), radius(radius), glyph(glyph) {}

    bool hit(const Ray& r, float tMin, float tMax, HitRecord& rec) const override {
        Vec3 oc = r.origin - center;
        float a = r.direction.dot(r.direction);
        float b = 2.0f * oc.dot(r.direction);
        float c = oc.dot(oc) - radius * radius;
        float discriminant = b * b - 4.0f * a * c;

        if (discriminant > 0.0f) {
            float temp = (-b - std::sqrt(discriminant)) / (2.0f * a);
            if (temp < tMax && temp > tMin) {
                rec.t = temp;
                rec.point = r.pointAt(rec.t);
                rec.normal = (rec.point - center) / radius;
                rec.materialGlyph = glyph;
                return true;
            }
            temp = (-b + std::sqrt(discriminant)) / (2.0f * a);
            if (temp < tMax && temp > tMin) {
                rec.t = temp;
                rec.point = r.pointAt(rec.t);
                rec.normal = (rec.point - center) / radius;
                rec.materialGlyph = glyph;
                return true;
            }
        }
        return false;
    }
};

class Plane : public SceneObject {
private:
    Vec3 point;
    Vec3 normal;
    char glyph;

public:
    Plane(const Vec3& point, const Vec3& normal, char glyph = '#')
        : point(point), normal(normal.normalized()), glyph(glyph) {}

    bool hit(const Ray& r, float tMin, float tMax, HitRecord& rec) const override {
        float denom = normal.dot(r.direction);
        if (std::abs(denom) > EPSILON) {
            float t = (point - r.origin).dot(normal) / denom;
            if (t >= tMin && t <= tMax) {
                rec.t = t;
                rec.point = r.pointAt(rec.t);
                rec.normal = (denom < 0.0f) ? normal : -normal;
                rec.materialGlyph = glyph;
                return true;
            }
        }
        return false;
    }
};

// ---------------------------------------------------------------------------
// Camera & Renderer
// ---------------------------------------------------------------------------
class Camera {
public:
    Vec3 position;
    Vec3 lookAt;
    Vec3 up;
    float fov;
    int width;
    int height;
    float aspectRatio;

    Camera(Vec3 pos, Vec3 target, Vec3 upVector, float fovDeg, int w, int h)
        : position(pos), lookAt(target), up(upVector), fov(fovDeg), width(w), height(h) {
        aspectRatio = static_cast<float>(width) / static_cast<float>(height);
    }

    Ray getRay(float u, float v) const {
        Vec3 forward = (lookAt - position).normalized();
        Vec3 right = forward.cross(up).normalized();
        Vec3 camUp = right.cross(forward).normalized();

        float theta = fov * static_cast<float>(PI) / 180.0f;
        float halfHeight = std::tan(theta / 2.0f);
        float halfWidth = aspectRatio * halfHeight;

        Vec3 imagePoint = position + forward
                        + (2.0f * u - 1.0f) * halfWidth * right
                        + (1.0f - 2.0f * v) * halfHeight * camUp;

        return Ray(position, imagePoint - position);
    }
};

// ---------------------------------------------------------------------------
// Main Scene Construction and Benchmark
// ---------------------------------------------------------------------------
class RayTracer {
private:
    std::vector<std::unique_ptr<SceneObject>> scene;
    Vec3 lightDirection;
    const std::string shades = " .:-=+*#%@";

public:
    RayTracer() : lightDirection(Vec3(0.5f, 1.0f, -0.8f).normalized()) {
        buildScene();
    }

    void buildScene() {
        // Large central sphere
        scene.push_back(std::make_unique<Sphere>(Vec3(0.0f, 0.0f, 3.0f), 1.0f, '@'));
        // Orbiting small spheres
        scene.push_back(std::make_unique<Sphere>(Vec3(-1.6f, 0.5f, 2.4f), 0.45f, '%'));
        scene.push_back(std::make_unique<Sphere>(Vec3(1.5f, -0.3f, 2.2f), 0.35f, '*'));
        // Floor plane
        scene.push_back(std::make_unique<Plane>(Vec3(0.0f, -1.0f, 0.0f), Vec3(0.0f, 1.0f, 0.0f), '.'));
    }

    char shade(const HitRecord& rec) const {
        float diffuse = rec.normal.dot(lightDirection);
        diffuse = std::max(0.0f, diffuse);

        // Checkerboard effect on horizontal plane
        if (rec.materialGlyph == '.') {
            int cx = static_cast<int>(std::floor(rec.point.x * 2.0f));
            int cz = static_cast<int>(std::floor(rec.point.z * 2.0f));
            bool isDark = ((cx + cz) % 2) != 0;
            if (isDark) diffuse *= 0.3f;
        }

        size_t idx = static_cast<size_t>(diffuse * (shades.size() - 1));
        idx = std::min(idx, shades.size() - 1);
        return shades[idx];
    }

    void render(int width, int height) {
        Camera cam(Vec3(0.0f, 0.8f, -0.5f), Vec3(0.0f, 0.0f, 2.5f), Vec3(0.0f, 1.0f, 0.0f), 65.0f, width, height);

        std::string frame;
        frame.reserve(width * height + height);

        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                float u = static_cast<float>(x) / static_cast<float>(width);
                float v = static_cast<float>(y) / static_cast<float>(height);

                Ray r = cam.getRay(u, v);
                HitRecord closestRec;
                float closestSoFar = 1000.0f;
                bool hitAnything = false;

                for (const auto& obj : scene) {
                    HitRecord tempRec;
                    if (obj->hit(r, EPSILON, closestSoFar, tempRec)) {
                        hitAnything = true;
                        closestSoFar = tempRec.t;
                        closestRec = tempRec;
                    }
                }

                if (hitAnything) {
                    frame += shade(closestRec);
                } else {
                    frame += ' ';
                }
            }
            frame += '\n';
        }

        std::cout << frame;
    }
};

// ---------------------------------------------------------------------------
// Execution Entry Point & Profiler
// ---------------------------------------------------------------------------
int main(int argc, char* argv[]) {
    std::cout << "========================================================\n";
    std::cout << " Maghraby Editor Stress Test - 3D ASCII Raymarcher\n";
    std::cout << "========================================================\n\n";

    int width = DEFAULT_WIDTH;
    int height = DEFAULT_HEIGHT;

    if (argc >= 3) {
        try {
            width = std::stoi(argv[1]);
            height = std::stoi(argv[2]);
        } catch (...) {
            width = DEFAULT_WIDTH;
            height = DEFAULT_HEIGHT;
        }
    }

    std::cout << "[INFO] Viewport Resolution: " << width << "x" << height << "\n";
    std::cout << "[INFO] Initializing scene and rendering...\n\n";

    auto start = std::chrono::high_resolution_clock::now();

    RayTracer tracer;
    tracer.render(width, height);

    auto end = std::chrono::high_resolution_clock::now();
    std::chrono::duration<double, std::milli> elapsed = end - start;

    std::cout << "\n========================================================\n";
    std::cout << " Benchmark Statistics:\n";
    std::cout << " - Total Rays Cast : " << (width * height) << "\n";
    std::cout << " - Render Time     : " << std::fixed << std::setprecision(2) << elapsed.count() << " ms\n";
    std::cout << " - Target Status   : SUCCESS\n";
    std::cout << "========================================================\n";

    return 0;
}
